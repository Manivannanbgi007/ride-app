import { SupportChat } from '@/components/business/Chat';
import { PageHeader } from '@/components/common/PageHeader';

/** The customer's chat with Support (one conversation, no list). */
export default function SupportPage() {
  return (
    <div className="mx-auto flex max-w-column flex-col gap-6">
      <PageHeader
        title="Support chat"
        subtitle="Questions about an order or your account? Write to us."
      />
      <SupportChat />
    </div>
  );
}
