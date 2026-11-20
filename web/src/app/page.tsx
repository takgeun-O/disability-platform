import HomePage from "@/features/HomePage";

export default async function Page({ searchParams }: PageProps<"/">) {
  const { tab } = await searchParams;
  return <HomePage weekly={tab === "weekly"} />;
}
