"use client"
import { useEffect, useLayoutEffect, useRef, type ReactNode } from "react"
export default function AdminSelectionPanel({id,title,focusOnOpen,onClose,children}:{id:number;title:string;focusOnOpen:boolean;onClose:()=>void;children:ReactNode}) {
  const heading=useRef<HTMLHeadingElement>(null),panel=useRef<HTMLElement>(null),body=useRef<HTMLDivElement>(null)
  // Only selection changes reset the reading position, never async content updates.
  useLayoutEffect(()=>{ if(body.current)body.current.scrollTop=0 },[id])
  useLayoutEffect(()=>{
    const element=panel.current
    if(!element)return
    let frame=0
    const measure=()=>{
      frame=0
      if(!window.matchMedia("(min-width:1101px)").matches){element.style.removeProperty("--preview-height");element.style.removeProperty("--preview-top");return}
      // The service header currently scrolls with the document. Measure its
      // visible bottom instead of reserving its full height after it leaves.
      const headerBottom=document.querySelector('.iyum-header')?.getBoundingClientRect().bottom??0
      const top=Math.max(16,headerBottom+16)
      element.style.setProperty("--preview-top",`${top}px`)
      const visibleTop=Math.max(top,element.getBoundingClientRect().top)
      element.style.setProperty("--preview-height",`${Math.max(0,window.innerHeight-visibleTop-16)}px`)
    }
    const schedule=()=>{if(!frame)frame=requestAnimationFrame(measure)}
    measure()
    window.addEventListener("scroll",schedule,{passive:true})
    window.addEventListener("resize",schedule)
    const observer=new ResizeObserver(schedule),header=document.querySelector('.iyum-header')
    if(header)observer.observe(header)
    return()=>{window.removeEventListener("scroll",schedule);window.removeEventListener("resize",schedule);observer.disconnect();cancelAnimationFrame(frame)}
  },[])
  useEffect(()=>{
    if(!focusOnOpen)return
    heading.current?.focus({preventScroll:true})
    if(window.matchMedia("(max-width:1100px)").matches)heading.current?.scrollIntoView({block:"start",behavior:"instant"})
  },[id,focusOnOpen])
  return <aside ref={panel} className="admin-preview" aria-label={title}>
    <header className="admin-preview-heading"><h2 ref={heading} tabIndex={-1}>{title}</h2><button type="button" onClick={onClose}>상세 닫기</button></header>
    <div ref={body} className="admin-preview-body" tabIndex={0} role="region" aria-label={`${title} 내용`}>{children}</div>
  </aside>
}
