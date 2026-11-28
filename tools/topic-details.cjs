// Topic browsing is independent of edit mode and does not record review progress.
module.exports.component = `
function M3({cards:i,part:t,state:n,onBack:l,onStart:o,onOpenCard:c,editMode:m}){
  const p=Zi.find(g=>g.id===t);
  const [hash,setHash]=Ae.useState(()=>location.hash);
  const opened=Ae.useRef(false),trigger=Ae.useRef(null);
  Ae.useEffect(()=>{
    const update=()=>setHash(location.hash);
    window.addEventListener("hashchange",update);
    return()=>window.removeEventListener("hashchange",update);
  },[]);
  const segments=hash.replace(/^#\\/?/,"").split("/");
  const selected=segments[0]==="topic"&&segments[1]===t&&segments[2]==="card"?segments[3]:null;
  function open(id,event){trigger.current=event.currentTarget;opened.current=true;location.hash="#topic/"+t+"/card/"+encodeURIComponent(id)}
  function close(){
    if(opened.current){opened.current=false;history.back()}
    else location.replace("#topic/"+t);
    if(trigger.current)trigger.current.focus();
  }
  return C.jsxs(C.Fragment,{children:[
    C.jsxs("header",{className:"page-head",children:[C.jsx("button",{className:"icon-btn",onClick:l,"aria-label":"返回",children:"←"}),C.jsx("h1",{children:p.name}),C.jsxs("span",{className:"counter",children:[i.length," 个知识点"]})]}),
    C.jsx("button",{className:"btn btn-primary btn-block",onClick:o,children:"复习这个板块"}),
    C.jsx("ul",{className:"topic-list",children:i.map(g=>{
      const w=n.progress[g.id],status=w?m3(w)?"due":"done":"new";
      return C.jsx("li",{className:"topic-entry",children:C.jsxs("button",{type:"button",className:"topic-item topic-open",onClick:event=>open(g.id,event),"aria-label":"查看知识点："+g.topic,"aria-haspopup":"dialog",children:[
        C.jsxs("div",{className:"topic-row",children:[C.jsx("span",{className:"topic-name",children:g.topic}),g.importance>1&&C.jsx("span",{className:"chip chip-warm",children:"重点"}),C.jsx("span",{className:"dot dot-"+status}),C.jsx("span",{className:"topic-chevron","aria-hidden":true,children:"›"})]}),
        C.jsxs("p",{className:"topic-preview",children:[g.preview,"…"]})
      ]})},g.id)
    })}),
    selected&&C.jsx(HuTaoKnowledgeDetail,{id:selected,onClose:close},selected)
  ]});
}
function HuTaoKnowledgeDetail({id,onClose}){
  const [card,setCard]=Ae.useState(null),[error,setError]=Ae.useState(null),[attempt,retry]=Ae.useState(0);
  const closeButton=Ae.useRef(null),panel=Ae.useRef(null);
  Ae.useEffect(()=>{
    const previous=document.body.style.overflow;
    document.body.style.overflow="hidden";
    if(closeButton.current)closeButton.current.focus();
    return()=>{document.body.style.overflow=previous};
  },[]);
  Ae.useEffect(()=>{
    let active=true;setError(null);
    k3(id).then(base=>{
      if(!active)return;
      const patch=fo(a3(),id);
      if(patch&&patch.op==="delete")throw new Error("这个知识点已删除，请关闭后重新选择。");
      setCard({...base,...(patch&&patch.fields||{})});
    }).catch(reason=>{if(active)setError(reason.message||"知识点读取失败")});
    return()=>{active=false};
  },[id,attempt]);
  function keydown(event){
    if(event.key==="Escape"){event.preventDefault();onClose()}
    if(event.key==="Tab"&&panel.current){
      const controls=panel.current.querySelectorAll('button:not([disabled]),a[href],[tabindex="0"]');
      const first=controls[0],last=controls[controls.length-1];
      if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}
      else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}
    }
  }
  const answers=card?card.answers.map((answer,index)=>[answer,...(card.accept?.[index]||[])]):[];
  return C.jsx("div",{className:"sheet-mask knowledge-mask",onClick:event=>{if(event.target===event.currentTarget)onClose()},children:
    C.jsxs("section",{className:"sheet knowledge-sheet",role:"dialog","aria-modal":true,"aria-labelledby":"knowledge-title",onKeyDown:keydown,ref:panel,children:[
      C.jsxs("header",{className:"sheet-head",children:[C.jsx("strong",{id:"knowledge-title",children:card?card.topic:"知识点详情"}),C.jsx("button",{type:"button",className:"icon-btn",ref:closeButton,onClick:onClose,"aria-label":"关闭知识点详情",children:"✕"})]}),
      C.jsx("div",{className:"sheet-body",children:error?C.jsxs("div",{role:"alert",children:[C.jsx("p",{className:"muted",children:error}),C.jsx("button",{type:"button",className:"btn btn-ghost",onClick:()=>retry(value=>value+1),children:"重新加载"})]}):card?C.jsxs(C.Fragment,{children:[
        C.jsx("div",{className:"knowledge-full",children:C.jsx(n3,{prompt:card.prompt,answers})}),
        C.jsx("h3",{className:"knowledge-label",children:"答案"}),
        C.jsx("ol",{className:"knowledge-answers",children:answers.map((values,index)=>C.jsx("li",{children:C.jsx(on,{text:t3(values)})},index))}),
        card.note&&C.jsxs("div",{className:"knowledge-note",children:[C.jsx("h3",{className:"knowledge-label",children:"解析 / 备注"}),C.jsx(on,{text:card.note})]})
      ]}):C.jsx("p",{className:"muted",role:"status",children:"正在加载知识点…"})})
    ]})
  });
}
`;
module.exports.css = `
.topic-entry{margin:0;padding:0;list-style:none}
.topic-open{display:block;width:100%;box-sizing:border-box;border:0;text-align:left;color:var(--ink);font-family:inherit;cursor:pointer;-webkit-tap-highlight-color:transparent;transition:background .15s,transform .15s}
.topic-open:active{background:var(--accent-soft);transform:scale(.99)}
.topic-open:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.topic-chevron{font-size:22px;line-height:1;color:#8ca49f;margin-left:3px}
.knowledge-mask{z-index:50;padding-top:16px;box-sizing:border-box}
.knowledge-sheet{max-height:85vh;max-height:85dvh}
.knowledge-sheet .sheet-head{gap:12px}
.knowledge-sheet .sheet-head strong{line-height:1.5}
.knowledge-sheet .icon-btn{flex-shrink:0}
.knowledge-sheet .sheet-body{overscroll-behavior:contain;padding-bottom:calc(28px + env(safe-area-inset-bottom))}
.knowledge-full{font-size:17px;line-height:1.95;overflow-wrap:anywhere;padding:12px 0 18px}
.knowledge-label{font-size:13px;color:var(--muted);margin:14px 0 8px;font-weight:600}
.knowledge-answers{padding:12px 12px 12px 34px;margin:0;background:var(--accent-soft);border-radius:12px;color:var(--accent);font-size:15px;line-height:1.9}
.knowledge-note{font-size:14px;line-height:1.9;white-space:pre-wrap;overflow-wrap:anywhere}
@media(prefers-reduced-motion:reduce){.topic-open{transition:none}}
`;
