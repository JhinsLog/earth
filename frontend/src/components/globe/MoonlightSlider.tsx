import { useSettingsStore } from '../../store/settingsStore'
import { trackAction } from '../../lib/telemetry'
import './MoonlightSlider.css'

/**
 * 휴대폰 달빛 막대. 해가 진 지역 전체를 밀어 올린 만큼 달빛으로 밝힌다.
 * 길게 눌러 비추는 손전등은 보려는 자리가 손가락에 가려서, 손이 지구 위에 없어도 밤 지역을 볼 수 있게 따로 둔다.
 */
export default function MoonlightSlider() {
  const moonlight = useSettingsStore((s) => s.moonlight)
  const setMoonlight = useSettingsStore((s) => s.setMoonlight)
  return (
    <label className="moonlight-slider">
      <span className="moonlight-slider__label">달빛</span>
      <input
        type="range"
        min={0}
        max={100}
        step={1}
        value={Math.round(moonlight * 100)}
        aria-label="밤 지역 달빛 밝기"
        onChange={(e) => setMoonlight(Number(e.target.value) / 100)}
        // 끌 때마다 남기면 로그가 넘친다. 손을 뗀 값만 남긴다.
        onPointerUp={(e) => trackAction('globe.moonlight', { level: Number(e.currentTarget.value) })}
      />
    </label>
  )
}
