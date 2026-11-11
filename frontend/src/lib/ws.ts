import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { refreshAccessToken } from './api'
import { useAuthStore } from '../store/authStore'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL

/** 재연결 직전에 이 시간 안에 만료될 토큰이면 미리 갱신한다. */
const TOKEN_REFRESH_MARGIN_MS = 30_000

/**
 * 화면이 원하는 구독 하나. 실제 STOMP 구독(sub)은 연결이 맺어질 때마다 새로 만든다.
 *
 * stompjs는 구독을 연결 하나에 묶어 두고, 연결이 끊기면 함께 버린다(재연결해도 복원하지 않는다).
 * 예전에는 구독을 한 번만 맺어서, 네트워크가 한 번 끊기거나 백엔드가 재배포되면 연결은 다시
 * 붙는데 채팅과 플레어 피드가 아무 표시 없이 멈췄다. 원하는 구독을 여기 따로 들고 있다가
 * 연결될 때마다 다시 건다.
 */
interface Wanted {
  destination: string
  onMessage: (message: IMessage) => void
  sub?: StompSubscription
  /** sub를 만든 연결. 이미 끊긴 연결의 구독을 해제하려 하면 stompjs가 예외를 던진다. */
  owner?: Client
}

const wanted = new Set<Wanted>()
const reconnectListeners = new Set<() => void>()

let client: Client | null = null
/** 현재 연결이 어떤 토큰으로 맺어졌는지. 로그인 상태가 바뀌었는지 판단하는 기준. */
let connectedWithToken: string | null = null
/** 페이지가 열린 뒤 한 번이라도 연결된 적이 있는지. 두 번째 연결부터가 "재연결"이다. */
let everConnected = false

function tokenExpiresAt(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

function ensureClient(): Client {
  const token = useAuthStore.getState().accessToken

  // 서버는 CONNECT 프레임의 토큰으로 세션의 신원을 한 번 정한다. 비로그인으로 맺은 연결을
  // 로그인 후에 그대로 쓰면 메시지가 신원 없이 조용히 무시된다. 토큰이 바뀌었으면 새로 맺는다.
  // 원하는 구독은 wanted에 남아 있으므로 새 연결이 맺어질 때 그대로 다시 걸린다.
  if (client && connectedWithToken !== token) {
    void client.deactivate()
    client = null
  }
  // 연결이 끊겨 재시도 중인 클라이언트도 그대로 쓴다. 여기서 새로 만들면 옛 클라이언트가 꺼지지
  // 않은 채 재시도를 계속해 연결이 두 개가 된다.
  if (client) return client

  const stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_BASE_URL) as WebSocket,
    reconnectDelay: 3000,
  })
  connectedWithToken = token

  // 재연결은 처음 연결 때의 헤더를 그대로 다시 보낸다. 액세스 토큰은 1시간이면 만료되는데,
  // 서버는 만료된 토큰의 CONNECT를 거절하지 않고 비로그인으로 받는다. 그러면 채팅방 구독이
  // 막히고 보낸 메시지도 무시된다. 연결할 때마다 토큰을 확인해 필요하면 먼저 갱신한다.
  let connectedWithExpiredToken = false
  stompClient.beforeConnect = async () => {
    let current = useAuthStore.getState().accessToken
    const expiresAt = current ? tokenExpiresAt(current) : null
    if (current && expiresAt !== null && expiresAt - Date.now() < TOKEN_REFRESH_MARGIN_MS) {
      // 서버가 재시작 중이라 갱신이 실패하면 null이 오지만 토큰은 남아 있다. 그대로 이어 간다.
      current = (await refreshAccessToken()) ?? useAuthStore.getState().accessToken
    }
    const currentExpiresAt = current ? tokenExpiresAt(current) : null
    connectedWithExpiredToken = currentExpiresAt !== null && currentExpiresAt <= Date.now()
    // 갱신으로 토큰이 바뀐 것은 로그인 상태 변화가 아니다. 기준을 맞춰 두지 않으면 다음
    // ensureClient가 이 연결을 끊고 다시 맺는다.
    connectedWithToken = current
    stompClient.connectHeaders = current ? { Authorization: `Bearer ${current}` } : {}
  }

  stompClient.onConnect = () => {
    // 갱신은 실패했는데 그사이 서버가 살아나 연결이 맺어지면, 서버는 만료된 토큰을 비로그인으로
    // 받는다. 그대로 두면 채팅이 막히므로 끊어서 재연결(=갱신 재시도)로 넘긴다.
    if (connectedWithExpiredToken) {
      stompClient.forceDisconnect()
      return
    }
    for (const w of wanted) {
      w.sub = stompClient.subscribe(w.destination, w.onMessage)
      w.owner = stompClient
    }
    // 끊긴 동안 온 메시지는 STOMP가 다시 주지 않는다. 화면이 REST로 다시 받아 채우게 알린다.
    if (everConnected) reconnectListeners.forEach((listener) => listener())
    everConnected = true
  }

  stompClient.onWebSocketClose = () => {
    for (const w of wanted) {
      if (w.owner === stompClient) {
        w.sub = undefined
        w.owner = undefined
      }
    }
  }

  client = stompClient
  stompClient.activate()
  return stompClient
}

/**
 * 토픽을 구독한다. 연결이 끊겼다 다시 붙어도, 토큰이 바뀌어 연결을 새로 맺어도 구독은 유지된다.
 * 해제 함수를 부를 때까지 계속 받는다.
 */
export async function subscribeTopic(
  destination: string,
  onMessage: (message: IMessage) => void,
): Promise<() => void> {
  const w: Wanted = { destination, onMessage }
  wanted.add(w)
  const c = ensureClient()
  if (c.connected) {
    w.sub = c.subscribe(destination, onMessage)
    w.owner = c
  }
  return () => {
    wanted.delete(w)
    if (w.sub && w.owner === client && client?.connected) w.sub.unsubscribe()
  }
}

/** 연결이 끊겼다가 다시 맺어질 때마다 불린다. 해제 함수를 돌려준다. */
export function onReconnect(listener: () => void): () => void {
  reconnectListeners.add(listener)
  return () => reconnectListeners.delete(listener)
}

/**
 * 메시지를 보낸다. 연결이 끊겨 있으면 기다리지 않고 바로 실패한다. 기다리게 하면 재연결이
 * 늦어질 때 전송 버튼이 "전송 중"에 멈춰 있고, 사용자는 보냈는지 알 수 없다.
 */
export async function publish(destination: string, body: unknown): Promise<void> {
  const c = ensureClient()
  if (!c.connected) throw new Error('연결이 끊겨 다시 연결하는 중입니다.')
  c.publish({ destination, body: JSON.stringify(body) })
}
