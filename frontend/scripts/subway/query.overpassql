[out:json][timeout:300];
area["ISO3166-1"="KR"][admin_level=2]->.kr;
(
  rel(area.kr)["type"="route"]["route"~"^(subway|light_rail|monorail)$"];
  rel(area.kr)["type"="route"]["route"="train"]["network"~"전철|도시철도|GTX|광역"];
)->.r;
.r out body;
way(r.r)["railway"];
out geom;
