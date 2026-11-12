[out:json][timeout:180];
area["ISO3166-1"="KR"][admin_level=2]->.kr;
node(area.kr)["railway"~"^(station|halt)$"];
out body qt;
