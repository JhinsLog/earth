import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../store/authStore'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL

let client: Client | null = null
let connectPromise: Promise<Client> | null = null

function getClient(): Promise<Client> {
  if (client?.connected) return Promise.resolve(client)
  if (connectPromise) return connectPromise

  const token = useAuthStore.getState().accessToken

  client = new Client({
    webSocketFactory: () => new SockJS(WS_BASE_URL) as WebSocket,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
  })

  const promise = new Promise<Client>((resolve, reject) => {
    client!.onConnect = () => resolve(client!)
    client!.onStompError = (frame) => reject(new Error(frame.headers.message))
    client!.activate()
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
