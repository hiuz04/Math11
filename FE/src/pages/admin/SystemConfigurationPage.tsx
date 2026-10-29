import PageHeading from "../../components/common/PageHeading";
import GeminiConfiguration from "../../components/admin/GeminiConfiguration";
import PromptConfiguration from "../../components/admin/PromptConfiguration";
export default function SystemConfigurationPage({ notify }: { notify: (message: string) => void }) {
  return <div className="page"><PageHeading eyebrow="QUẢN TRỊ" title="Cài đặt hệ thống" subtitle="Quản lý cấu hình Gemini và Prompt hướng dẫn học tập."/><div className="main-column"><GeminiConfiguration notify={notify}/><PromptConfiguration notify={notify}/></div></div>;
}
