import Screen from "@/features/PostCreate";
import { connection } from "next/server";
export default async function Page(){await connection();return <Screen />;}
