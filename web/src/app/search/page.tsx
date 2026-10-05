import Screen from "@/features/SearchResults";
import { connection } from "next/server";
export default async function Page(){await connection();return <Screen />;}
