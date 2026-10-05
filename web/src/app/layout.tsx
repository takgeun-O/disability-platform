import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Header, GlobalNav, Footer } from "@/components/Shell";
import { NavigationGuardProvider } from "@/components/NavigationGuard";
import "../index.css";
export const metadata: Metadata = {title: "IYUM", description: "장애인이 필요한 정보를 찾는 시간과 어려움을 줄이고, 공식 정보와 실제 경험을 연결하여 더 나은 선택을 돕습니다.", robots: { index: false, follow: false }};
export default function RootLayout({children}: {children: ReactNode}){
 return <html lang="ko"><body><NavigationGuardProvider><div className="min-h-screen flex flex-col" style={{backgroundColor:'#F6F6F4',color:'#1A1918',fontFamily:"'Noto Sans KR', sans-serif"}}><Header /><GlobalNav /><div style={{flex:1}}>{children}</div><Footer /></div></NavigationGuardProvider></body></html>;
}
