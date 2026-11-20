-- 화산 플레어를 '기타'에서 전용 분류로 옮긴다(D-25). 열 형식은 그대로(VARCHAR)라 값만 바꾼다.
-- 동기화(6시간마다)도 결국 같은 값으로 고치지만, 배포 직후부터 화면 색이 맞도록 바로 옮긴다.
UPDATE flares SET category = 'VOLCANO' WHERE source = 'SI_GVP_WVAR' AND category = 'ETC';
