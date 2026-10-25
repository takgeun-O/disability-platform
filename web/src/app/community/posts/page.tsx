import CommunityListPage, {
  type ListPageProps,
} from "@/components/community/CommunityListPage";
export default function PostsPage(props: ListPageProps) {
  return <CommunityListPage {...props} pathname="/community/posts" />;
}
