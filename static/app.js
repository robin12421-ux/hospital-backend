let segmentChart, spendChart, scatterChart;
const $ = id => document.getElementById(id);
const fmt = v => v === null || v === undefined ? "—" : Number(v).toLocaleString(undefined,{maximumFractionDigits:2});
const palette = ["#6d5dfc","#32b8a6","#ffb85c","#5b9df9","#f2769b","#8b7df7","#8a9aac"];
function chartBase(){return {responsive:true, maintainAspectRatio:false, plugins:{legend:{labels:{usePointStyle:true,boxWidth:8,color:"#64748b",font:{family:"DM Sans"}}}}, scales:{x:{grid:{display:false},ticks:{color:"#8793a7"},border:{display:false}},y:{grid:{color:"#eef1f6"},ticks:{color:"#8793a7"},border:{display:false}}}}}
function showToast(msg){const t=$("toast");t.textContent=msg;t.classList.add("show");setTimeout(()=>t.classList.remove("show"),3000)}
async function loadAnalysis(data=null){
  if(!data){const res=await fetch("/api/analysis");data=await res.json()}
  $("mRows").textContent=fmt(data.rows);$("mCols").textContent=fmt(data.columns_count);
  $("mComplete").textContent=data.complete_percent+"%";$("mDup").textContent=fmt(data.duplicates);
  $("missingCount").textContent=fmt(data.missing);$("qualityPercent").textContent=data.complete_percent+"%";
  $("outlierCount").textContent=Object.values(data.outliers||{}).reduce((a,b)=>a+b,0);
  $("fileName").textContent=data.filename;
  renderSegments(data);renderSpending(data);renderScatter(data);renderStats(data);renderPreview(data);
}
function renderSegments(d){
 const entries=Object.entries(d.segments||{}); const labels=entries.map(x=>x[0]), vals=entries.map(x=>x[1]);
 if(segmentChart)segmentChart.destroy();
 segmentChart=new Chart($("segmentChart"),{type:"bar",data:{labels,datasets:[{label:"Customers",data:vals,backgroundColor:palette,borderRadius:7,maxBarThickness:46}]},options:{...chartBase(),plugins:{legend:{display:false}},scales:{...chartBase().scales,y:{...chartBase().scales.y,beginAtZero:true}}}});
 const top=entries.sort((a,b)=>b[1]-a[1])[0];
 $("segmentSummary").textContent=top?`${top[0]} is the largest displayed segment (${top[1]} records). Use segments to tailor offers and communication.`:"No segment/category column found.";
}
function renderSpending(d){
 const e=Object.entries(d.spending_bins||{}); if(spendChart)spendChart.destroy();
 spendChart=new Chart($("spendChart"),{type:"doughnut",data:{labels:e.map(x=>x[0]),datasets:[{data:e.map(x=>x[1]),backgroundColor:["#ffb85c","#6d5dfc","#32b8a6"],borderWidth:0,hoverOffset:6}]},options:{responsive:true,maintainAspectRatio:false,cutout:"72%",plugins:{legend:{position:"bottom",labels:{usePointStyle:true,boxWidth:8,padding:18,color:"#64748b"}}}}});
 $("spendSummary").innerHTML=e.map((x,i)=>`<div><i style="background:${["#ffb85c","#6d5dfc","#32b8a6"][i]}"></i><span>${x[0]}</span><b>${x[1]}</b></div>`).join("")||"SpendingScore column not found.";
}
function renderScatter(d){
 const rows=d.preview||[]; const pts=rows.filter(r=>r.AnnualIncome!=null&&r.SpendingScore!=null).map(r=>({x:Number(r.AnnualIncome),y:Number(r.SpendingScore)}));
 if(scatterChart)scatterChart.destroy();
 scatterChart=new Chart($("scatterChart"),{type:"scatter",data:{datasets:[{label:"Customers",data:pts,backgroundColor:"#6d5dfc",pointRadius:5,pointHoverRadius:7}]},options:{...chartBase(),plugins:{legend:{display:false}},scales:{x:{...chartBase().scales.x,title:{display:true,text:"Annual income",color:"#8793a7"}},y:{...chartBase().scales.y,title:{display:true,text:"Spending score",color:"#8793a7"}}}}});
 const c=d.correlation;
 $("corrSummary").textContent=c===null||c===undefined?"For this chart, use columns named AnnualIncome and SpendingScore.":`Pearson correlation: ${c}. ${Math.abs(c)<.3?"Weak":Math.abs(c)<.7?"Moderate":"Strong"} ${c>=0?"positive":"negative"} linear relationship in the dataset. Correlation does not prove causation.`;
}
function renderStats(d){
 const body=$("statsBody");const entries=Object.entries(d.stats||{});
 body.innerHTML=entries.map(([k,s])=>`<tr><td><b>${k}</b></td><td>${fmt(s.mean)}</td><td>${fmt(s.median)}</td><td>${fmt(s.mode)}</td><td>${fmt(s.variance)}</td><td>${fmt(s.std)}</td><td>${fmt(s.min)} – ${fmt(s.max)}</td></tr>`).join("")||'<tr><td colspan="7">No numeric columns detected.</td></tr>';
}
function renderPreview(d){
 const cols=d.columns||[]; $("previewCount").textContent=`${Math.min(12,d.rows)} of ${d.rows} rows`;
 $("previewHead").innerHTML="<tr>"+cols.map(c=>`<th>${c}</th>`).join("")+"</tr>";
 $("previewBody").innerHTML=(d.preview||[]).map(row=>"<tr>"+cols.map(c=>`<td>${row[c]===null||row[c]===undefined?"—":String(row[c])}</td>`).join("")+"</tr>").join("");
}
async function cleanData(){const res=await fetch("/api/clean",{method:"POST"});const d=await res.json();await loadAnalysis(d);showToast(`Dataset cleaned. Removed ${d.cleaning.duplicates_removed} duplicate rows.`)}
async function resetData(){const res=await fetch("/api/reset",{method:"POST"});const d=await res.json();await loadAnalysis(d);showToast("Demo dataset restored.")}
document.addEventListener("DOMContentLoaded",()=>loadAnalysis().catch(()=>showToast("Could not load analytics. Check that Flask is running.")));
