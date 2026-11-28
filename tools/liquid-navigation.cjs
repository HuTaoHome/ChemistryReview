// Applied by customize-web.cjs so subsequent website syncs retain Android navigation.
module.exports.component = `
function HuTaoNavigation(){
  const [route,setRoute]=Ae.useState(()=>location.hash);
  const [keyboard,setKeyboard]=Ae.useState(false);
  Ae.useEffect(()=>{
    const update=()=>setRoute(location.hash);
    const viewport=window.visualViewport;
    const resize=()=>setKeyboard(!!viewport&&window.innerHeight-viewport.height>150);
    window.addEventListener("hashchange",update);
    if(viewport)viewport.addEventListener("resize",resize);
    return()=>{window.removeEventListener("hashchange",update);if(viewport)viewport.removeEventListener("resize",resize)};
  },[]);
  const settings=route==="#settings";
  const main=!route||route==="#"||route==="#/";
  const visible=(main||settings)&&!keyboard;
  const icon=(type)=>C.jsx("svg",{viewBox:"0 0 24 24",width:21,height:21,fill:"none",stroke:"currentColor",strokeWidth:1.7,strokeLinecap:"round",strokeLinejoin:"round","aria-hidden":true,children:
    type==="home"?C.jsx("path",{d:"M3.5 10.5 12 3.8l8.5 6.7v9a1 1 0 0 1-1 1H15v-7H9v7H4.5a1 1 0 0 1-1-1z"}):
    C.jsxs(C.Fragment,{children:[C.jsx("path",{d:"m9.2 4 .5-2h4.6l.5 2 1.7 1 2-.6 2.3 4-1.5 1.5v2.2l1.5 1.5-2.3 4-2-.6-1.7 1-.5 2H9.7l-.5-2-1.7-1-2 .6-2.3-4 1.5-1.5V9.9L3.2 8.4l2.3-4 2 .6z",transform:"translate(0 1) scale(1 .95)"}),C.jsx("circle",{cx:12,cy:12,r:3})]})});
  return C.jsxs("div",{className:visible?"bottom-dock":"bottom-dock credit-only",children:[
    C.jsx("footer",{className:"designer-credit",children:"Designed By HuTao"}),
    visible&&C.jsxs("nav",{className:"liquid-nav"+(settings?" is-settings":""),"aria-label":"主导航",children:[
      C.jsxs("button",{type:"button",className:"liquid-tab"+(!settings?" is-active":""),"aria-current":!settings?"page":undefined,onClick:()=>{if(!main)location.hash="#"},children:[icon("home"),C.jsx("span",{children:"主页"})]}),
      C.jsxs("button",{type:"button",className:"liquid-tab"+(settings?" is-active":""),"aria-current":settings?"page":undefined,onClick:()=>{if(!settings)location.hash="#settings"},children:[icon("settings"),C.jsx("span",{children:"设置"})]})
    ]})
  ]});
}
`;

module.exports.css = `
.app{padding-bottom:128px}
.bottom-dock{position:fixed;left:50%;bottom:calc(12px + env(safe-area-inset-bottom));transform:translateX(-50%);z-index:35;display:flex;flex-direction:column;align-items:center;gap:9px;pointer-events:none}
.bottom-dock.credit-only{bottom:calc(6px + env(safe-area-inset-bottom))}
.designer-credit{order:2;font-size:10px;line-height:1.4;color:#7b8987;pointer-events:none;text-shadow:0 1px 4px #fff}
.liquid-nav{isolation:isolate;position:relative;display:flex;width:248px;height:56px;box-sizing:border-box;padding:5px;border-radius:32px;border:1px solid rgba(255,255,255,.92);background:rgba(244,249,249,.97);box-shadow:0 8px 26px rgba(24,63,62,.13),0 2px 6px rgba(24,63,62,.05),inset 0 1px 0 #fff,inset 0 -1px 1px rgba(255,255,255,.6);pointer-events:auto}
@supports ((backdrop-filter:blur(1px)) or (-webkit-backdrop-filter:blur(1px))){.liquid-nav{background:linear-gradient(125deg,rgba(255,255,255,.74),rgba(235,246,246,.48));backdrop-filter:blur(22px) saturate(1.6);-webkit-backdrop-filter:blur(22px) saturate(1.6)}}
.liquid-nav::before{content:"";position:absolute;z-index:-1;top:5px;bottom:5px;left:5px;width:calc((100% - 10px)/2);border-radius:25px;background:linear-gradient(135deg,rgba(15,118,110,.17),rgba(73,156,150,.08));box-shadow:inset 0 1px 1px rgba(255,255,255,.95),0 1px 4px rgba(13,94,88,.07);transition:transform .32s cubic-bezier(.22,.8,.25,1)}
.liquid-nav.is-settings::before{transform:translateX(100%)}
.liquid-tab{flex:1;min-width:0;display:flex;align-items:center;justify-content:center;gap:8px;padding:0;border:0;border-radius:25px;background:transparent;color:#788784;font-family:inherit;font-size:13px;font-weight:500;cursor:pointer;-webkit-tap-highlight-color:transparent;transition:color .2s,transform .2s}
.liquid-tab.is-active{color:#0f766e;font-weight:650}
.liquid-tab:active{transform:scale(.95)}
.liquid-tab:focus-visible{outline:2px solid #0f766e;outline-offset:-3px}
.project-link{display:inline-block;color:#0f766e;text-decoration:underline;overflow-wrap:anywhere;padding:6px 0}
@media(prefers-reduced-motion:reduce){.liquid-nav::before,.liquid-tab{transition:none}}
`;
