import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../store/authStore'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL

let client: Client | null = null
let connectPromise: Promise<Client> | null = null
/** 현재 연결이 어떤 토큰으로 맺어졌는지. 로그인 상태가 바뀌었는지 판단하는 기준. */
let connectedWithToken: string | null = null

function getClient(): Promise<Client> {
  const token = useAuthStore.getState().accessToken

  // 서버는 CONNECT 프레임의 토큰으로 세션의 신원을 한 번 정한다. 따라서 비로그인 상태로
  // 맺어둔 연결을 로그인 후에 그대로 재사용하면, 메시지를 보내도 서버에서 신원이 없어
  // 조용히 무시된다. 토큰이 바뀌었으면 연결을 새로 맺는다.
  if ((client || connectPromise) && connectedWithToken !== token) {
    void client?.deactivate()
    client = null
    connectPromise = null
  }

  if (client?.connected) return Promise.resolve(client)
  if (connectPromise) return connectPromise

  const stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_BASE_URL) as WebSocket,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
  })
  client = stompClient
  connectedWithToken = token

  const promise = new Promise<Client>((resolve, reject) => {
    stompClient.onConnect = () => resolve(stompClient)
    stompClient.onStompError = (frame) => reject(new Error(frame.headers.message ?? 'STOMP 연결 실패'))
    stompClient.onWebSocketError = () => reject(new Error('WebSocket 연결 실패'))
    stompClient.activate()
  }).finally(() => {
    connectPromise = null
  })

  connectPromise = promise
  return promise
}

export async function subscribeTopic(
  destination: string,
  onMessage: (message: IMessage) => void,
): Promise<() => void> {
  const c = await getClient()
  const subscription = c.subscribe(destination, onMessage)
  return () => subscription.unsubscribe()
}

export async function publish(destination: string, body: unknown): Promise<void> {
  const c = await getClient()
  c.publish({ destination, body: JSON.stringify(body) })
}
