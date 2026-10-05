import Screen from "@/features/Login";
import { connection } from "next/server";
export default async function Page(){await connection();return <Screen />;}
