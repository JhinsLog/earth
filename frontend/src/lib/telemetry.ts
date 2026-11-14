import { useAuthStore } from '../store/authStore'

/**
 * 브라우저에서 일어난 오류와 사용자 행동을 모아 백엔드(POST /api/client-events)로 보낸다. 백엔드가 Loki에 쌓고
 * Grafana의 'Earth 로그' 대시보드에서 본다.
 *
 * 이게 없을 때는 휴대폰에서 화면이 굳어도 원인을 알 방법이 없었다(2026-11-14, 갤럭시 S23 Ultra). 그런 문제는
 * 사용자 기기 안에서만 일어나 서버 로그에는 흔적이 없다.
 *
 * 보내지 않는 것: 채팅 본문, 좌표, 입력한 글. 무엇을 했는지만 남긴다. 세션 ID는 탭마다 새로 만드는 무작위 값이다.
 *
 * 10초마다 묶어 보낸다. 행동 하나마다 요청을 보내면 지도를 조작할 때 요청이 쏟아진다. 탭을 닫거나 다른 앱으로 갈 때는
 * keepalive 요청으로 남은 것을 보낸다(페이지가 사라져도 브라우저가 끝까지 보낸다).
 */

// lib/api.ts의 API_BASE_URL을 import하지 않는다. api.ts가 이 파일을 import하므로(5xx 기록) 순환이 생기고,
// 먼저 평가되는 쪽에 따라 값이 아직 없는 채로 읽혀 시작하자마자 ReferenceError가 난다.
const ENDPOINT = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/client-events`
const FLUSH_INTERVAL_MS = 10_000
/** 서버 DTO의 상한(ClientEventBatch)과 맞춘다. 넘으면 묶음 전체가 400으로 버려진다. */
const MAX_EVENTS_PER_BATCH = 30
const MAX_DATA_ENTRIES = 10
/** keepalive 요청은 브라우저가 64KB까지만 받는다(서버 한도도 같다). 여유를 둔다. */
const MAX_BODY_BYTES = 60_000
/** 한 탭이 보낼 수 있는 총량. 무한 루프 안의 오류가 수천 건을 보내지 않게 한다. */
const MAX_EVENTS_PER_SESSION = 500
/** 같은 오류(이름+메시지)는 탭당 이만큼만. 매 프레임 나는 오류가 다른 기록을 밀어내지 않게 한다. */
const MAX_SAME_ERROR = 5

type Kind = 'ERROR' | 'ACTION'
type Data = Record<string, string | number | boolean | null | undefined>

interface ClientEvent {
  kind: Kind
  name: string
  message?: string
  stack?: string
  path: string
  at: number
  data?: Record<string, string>
}

const queue: ClientEvent[] = []
const errorCounts = new Map<string, number>()
let queuedTotal = 0
let gpu: string | undefined
let started = false

const sessionId = (() => {
  const make = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`)
  try {
    const saved = sessionStorage.getItem('earth.sessionId')
    if (saved) return saved
    const id = make()
    sessionStorage.setItem('earth.sessionId', id)
    return id
  } catch {
    return make()
  }
})()

/** 서버의 키 규칙([a-zA-Z0-9_], 32자)·값 200자에 맞춘다. 값이 없는 항목은 뺀다. */
function cleanData(data?: Data): Record<string, string> | undefined {
  if (!data) return undefined
  const entries = Object.entries(data)
    .filter(([, value]) => value !== undefined && value !== null)
    .slice(0, MAX_DATA_ENTRIES)
    .map(([key, value]) => [key.replace(/[^a-zA-Z0-9_]/g, '_').slice(0, 32), String(value).slice(0, 200)])
  return entries.length ? Object.fromEntries(entries) : undefined
}

function enqueue(event: Omit<ClientEvent, 'path' | 'at'>) {
  if (queuedTotal >= MAX_EVENTS_PER_SESSION) return
  queuedTotal += 1
  queue.push({ ...event, path: location.pathname.slice(0, 200), at: Date.now() })
  if (queue.length >= MAX_EVENTS_PER_BATCH) flush()
}

/** 사용자가 화면에서 한 일. name은 소문자·숫자·점으로(예: 'view.map', 'flare.open'). */
export function trackAction(name: string, data?: Data) {
  enqueue({ kind: 'ACTION', name, data: cleanData(data) })
}

/** 오류. error가 Error면 메시지와 스택을 같이 보낸다. */
export function trackError(name: string, error?: unknown, data?: Data) {
  const message = (error instanceof Error ? error.message : error == null ? undefined : String(error))?.slice(0, 1000)
  const key = `${name}|${message ?? ''}`
  const count = (errorCounts.get(key) ?? 0) + 1
  errorCounts.set(key, count)
  if (count > MAX_SAME_ERROR) return
  const stack = error instanceof Error ? error.stack?.slice(0, 4000) : undefined
  enqueue({ kind: 'ERROR', name, message, stack, data: cleanData(data) })
}

/**
 * GPU 이름. 화면 굳음 같은 오류가 특정 기기에서만 나는지 가르는 핵심 정보다. 새 WebGL 컨텍스트를 만들어 읽지 않고
 * 이미 있는 것(라스·지도)에서 한 번만 읽는다 — 휴대폰에서 컨텍스트를 하나 더 만들면 그 자체로 GPU 메모리를 쓴다.
 */
export function rememberGpu(gl: WebGLRenderingContext | WebGL2RenderingContext) {
  if (gpu) return
  try {
    const ext = gl.getExtension('WEBGL_debug_renderer_info')
    const name = gl.getParameter(ext ? ext.UNMASKED_RENDERER_WEBGL : gl.RENDERER)
    gpu = typeof name === 'string' ? name.slice(0, 200) : undefined
  } catch {
    // 읽지 못해도 로그는 보낸다.
  }
}

function clientInfo() {
  return {
    userAgent: navigator.userAgent.slice(0, 300),
    gpu,
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    pixelRatio: window.devicePixelRatio || undefined,
    language: navigator.language?.slice(0, 35),
  }
}

const encoder = new TextEncoder()

function flush(keepalive = false) {
  while (queue.length) {
    let events = queue.splice(0, MAX_EVENTS_PER_BATCH)
    let body = JSON.stringify({ sessionId, client: clientInfo(), events })
    // 스택이 긴 오류가 몰리면 64KB를 넘는다. 반씩 덜어 내 다음 묶음으로 넘긴다.
    while (encoder.encode(body).length > MAX_BODY_BYTES && events.length > 1) {
      const half = Math.ceil(events.length / 2)
      queue.unshift(...events.slice(half))
      events = events.slice(0, half)
      body = JSON.stringify({ sessionId, client: clientInfo(), events })
    }
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    // 로그인 중이면 누가 겪은 일인지 서버가 토큰으로 확인해 붙인다. 만료된 토큰이면 서버는 비로그인으로 받는다.
    // lib/api.ts의 axios 인스턴스를 쓰지 않는 이유: axios는 keepalive를 지원하지 않아 탭을 닫을 때 마지막 묶음이
    // 사라지고, 401 재시도 인터셉터가 로그 전송 때문에 토큰을 갱신하는 것도 원하지 않는다.
    const token = useAuthStore.getState().accessToken
    if (token) headers.Authorization = `Bearer ${token}`
    // 실패는 조용히 버린다. 로그 전송 실패를 다시 오류로 기록하면 끝없이 돈다.
    fetch(ENDPOINT, { method: 'POST', headers, body, keepalive }).catch(() => {})
  }
}

/** 앱 시작 시 한 번. 전역 오류를 잡고, 주기 전송과 떠날 때 전송을 건다. */
export function startTelemetry() {
  if (started) return
  started = true

  window.addEventListener('error', (event) => {
    trackError('js.error', event.error ?? event.message, {
      source: event.filename ? `${event.filename}:${event.lineno}:${event.colno}` : undefined,
    })
  })
  window.addEventListener('unhandledrejection', (event) => {
    // axios 실패는 lib/api.ts가 이미 'api.error'로 남겼다(5xx·네트워크). 4xx는 정상 흐름이다. 여기서 또 남기면
    // 같은 실패가 두 줄씩 쌓여 오류 수가 부풀려진다.
    if ((event.reason as { isAxiosError?: boolean } | null)?.isAxiosError) return
    trackError('js.unhandled_rejection', event.reason)
  })

  window.setInterval(() => flush(), FLUSH_INTERVAL_MS)
  // 휴대폰은 탭을 닫기 전에 거의 항상 '숨김' 상태를 거친다(unload는 휴대폰에서 자주 불리지 않는다).
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush(true)
  })
  window.addEventListener('pagehide', () => flush(true))

  trackAction('page.view', { referrer: document.referrer ? new URL(document.referrer).host : undefined })
}
