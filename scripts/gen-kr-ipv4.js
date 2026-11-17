// 국내 IP 대역 리소스(src/main/resources/geo/kr-ipv4.txt)를 다시 만든다.
// 1) APNIC 할당 목록을 받는다:  curl -o delegated.txt https://ftp.apnic.net/stats/apnic/delegated-apnic-latest
// 2) node scripts/gen-kr-ipv4.js delegated.txt src/main/resources/geo/kr-ipv4.txt
// 새 대역은 다음 방문부터 적용된다. 이미 판별된 visitor.ip_origin 은 바뀌지 않는다(NULL 인 행만 기동 시 채운다).
const fs=require('fs');const [src,out]=process.argv.slice(2);
const lines=fs.readFileSync(src,'utf8').split('\n');
const hdr=lines.find(l=>l.startsWith('2|apnic|'));const date=hdr.split('|')[2];
const toN=s=>s.split('.').reduce((a,b)=>a*256+ +b,0);
const toS=n=>[24,16,8,0].map(s=>Math.floor(n/2**s)%256).join('.');
let r=lines.filter(l=>/\|KR\|ipv4\|/.test(l)).map(l=>{const p=l.split('|');const s=toN(p[3]);return [s,s+ +p[4]];}).sort((a,b)=>a[0]-b[0]);
const m=[];for(const x of r){const l=m[m.length-1];if(l&&x[0]<=l[1])l[1]=Math.max(l[1],x[1]);else m.push([...x]);}
fs.writeFileSync(out,`# 한국(KR) 에 할당된 IPv4 대역 — APNIC delegated-apnic-latest (${date}) 에서 뽑아 인접 대역을 합쳤다.\n# 형식: <시작 IP> <주소 개수>. 갱신: scripts/gen-kr-ipv4.js 참고\n`+m.map(([s,e])=>`${toS(s)} ${e-s}`).join('\n')+'\n');
console.log(r.length,'->',m.length);
