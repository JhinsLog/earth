import { useState } from 'react'
import './Starfield.css'

interface Star {
  top: string
  left: string
  size: number
  delay: string
  duration: string
  opacity: number
}

/** 지도 컨테이너 뒤에 깔리는 은은하게 반짝이는 우주 배경(별). */
export default function Starfield() {
  const [stars] = useState<Star[]>(() =>
    Array.from({ length: 150 }).map(() => ({
      top: `${Math.random() * 100}%`,
      left: `${Math.random() * 100}%`,
      size: Math.random() * 1.8 + 0.8,
      delay: `${Math.random() * 6}s`,
      duration: `${Math.random() * 4 + 3}s`,
      opacity: Math.random() * 0.7 + 0.3,
    })),
  )

  return (
    <div className="starfield">
      {stars.map((star, i) => (
        <span
          key={i}
          className="starfield__star"
          style={{
            top: star.top,
            left: star.left,
            width: `${star.size}px`,
            height: `${star.size}px`,
            opacity: star.opacity,
            boxShadow: star.size > 1.8 ? '0 0 6px rgba(255, 255, 255, 0.8)' : 'none',
            animationDuration: star.duration,
            animationDelay: star.delay,
          }}
        />
      ))}
    </div>
  )
}
