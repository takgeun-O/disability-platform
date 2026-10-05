import Screen from "@/features/PostDetail";
import { connection } from "next/server";
export default async function Page(){await connection();return <Screen />;}
