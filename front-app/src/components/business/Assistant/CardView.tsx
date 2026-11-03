import { EntitiesCard } from '@/components/business/Assistant/EntitiesCard';
import { StatsCard } from '@/components/business/Assistant/StatsCard';
import { TableCard } from '@/components/business/Assistant/TableCard';
import type { AssistantCard } from '@/types/assistant';

/** One card of an answer, by its kind (a kind this version does not know is not drawn). */
export function CardView({ card }: { card: AssistantCard }) {
  switch (card.kind) {
    case 'stats':
      return <StatsCard card={card} />;
    case 'table':
      return <TableCard card={card} />;
    case 'entities':
      return <EntitiesCard card={card} />;
    default:
      return null;
  }
}
