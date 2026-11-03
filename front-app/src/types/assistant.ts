/** Types of the AI assistant API (ai-mcp-client behind the gateway: /api/v1/assistant/**). */

/** The longest question the assistant accepts. */
export const ASSISTANT_MESSAGE_MAX_LENGTH = 1000;

export interface AssistantLink {
  label: string;
  /** An address inside this application (checked by safeAppPath before it is used) */
  path: string;
}

export interface StatTile {
  label: string;
  value: string;
  hint: string | null;
}

export interface StatsCard {
  kind: 'stats';
  title: string;
  stats: StatTile[];
}

export interface TableColumn {
  key: string;
  label: string;
  align: 'left' | 'right';
}

export interface TableCard {
  kind: 'table';
  title: string;
  columns: TableColumn[];
  rows: Array<Record<string, string | number | null>>;
}

export const ENTITY_TYPES = ['RESTAURANT', 'FOOD', 'OFFER', 'ORDER', 'DELIVERY'] as const;
export type EntityType = (typeof ENTITY_TYPES)[number];

export interface EntityItem {
  id: string;
  title: string;
  subtitle: string | null;
  badges: string[];
  meta: Array<{ label: string; value: string }>;
  imageUrl: string | null;
  link: AssistantLink | null;
}

export interface EntitiesCard {
  kind: 'entities';
  entityType: EntityType;
  title: string;
  items: EntityItem[];
}

export type AssistantCard = StatsCard | TableCard | EntitiesCard;

export interface OrderProposalLine {
  name: string;
  quantity: number;
  unitPrice: number | null;
  lineTotal: number | null;
}

export interface OrderProposalSummary {
  restaurantName: string;
  items: OrderProposalLine[];
  deliveryAddress: string;
  paymentMethod: string;
  itemsTotal: number | null;
  discount: number | null;
  deliveryFee: number | null;
  tax: number | null;
  total: number | null;
  currency: string;
  notes: string | null;
  warnings: string[];
}

/** An order the assistant prepared; nothing is ordered until the user confirms. */
export interface OrderPendingAction {
  actionId: string;
  kind: 'PLACE_ORDER';
  summary: OrderProposalSummary;
  /** ISO instant */
  expiresAt: string;
}

/** What a prepared change will do, as the user checks it before confirming. */
export interface ChangeDetails {
  /** "Cancel order #42", "Picked up: delivery #31 (order #42)" */
  title: string;
  rows: Array<{ label: string; value: string }>;
  /** For example "The amount paid online is refunded automatically." */
  note: string | null;
  /** The Confirm button's text: "Cancel order", "Accept order", "Picked up"... */
  confirmLabel: string;
}

/**
 * Any other change the assistant prepared (cancel an order, accept it, mark a dish sold out, mark a delivery picked
 * up, reassign a delivery...). Nothing changes until the user confirms.
 */
export interface ChangePendingAction {
  actionId: string;
  /** CANCEL_MY_ORDER, ACCEPT_ORDER, MARK_PICKED_UP, REASSIGN_DELIVERY... (only shown, never interpreted) */
  kind: string;
  details: ChangeDetails;
  /** ISO instant */
  expiresAt: string;
}

export type PendingAction = OrderPendingAction | ChangePendingAction;

export function isOrderProposal(action: PendingAction): action is OrderPendingAction {
  return action.kind === 'PLACE_ORDER' && 'summary' in action;
}

/** What the assistant answered (text, then cards, then links). */
export interface AssistantReply {
  text: string;
  cards: AssistantCard[];
  links: AssistantLink[];
  pendingAction: PendingAction | null;
  toolsUsed: string[];
}

export interface AssistantChatRequest {
  conversationId: string | null;
  message: string;
}

export interface AssistantChatResponse {
  conversationId: string;
  messageId: string;
  reply: AssistantReply;
  model: string | null;
  durationMs: number;
}

export interface AssistantHistoryMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  text: string;
  cards: AssistantCard[];
  links: AssistantLink[];
  pendingAction: PendingAction | null;
  at: string | null;
}

export interface AssistantConversation {
  conversationId: string;
  messages: AssistantHistoryMessage[];
}

export interface ConfirmActionResponse {
  /** CREATED: an order was placed; DONE: another change was made (ALREADY_: by an earlier press) */
  status: 'CREATED' | 'ALREADY_CREATED' | 'DONE' | 'ALREADY_DONE';
  /** Only when an order was placed */
  order: { id: string; status: string; total: number | null; currency: string } | null;
  message: string;
  link: AssistantLink | null;
}

export interface AssistantStatus {
  available: boolean;
  model: string | null;
  message: string | null;
}

/** How a proposal ended (kept by the provider so closing the panel does not bring the buttons back). */
export type ActionResolution =
  | { status: 'confirmed'; message: string; link: AssistantLink | null }
  | { status: 'cancelled' }
  | { status: 'expired' }
  | { status: 'failed'; message: string };

/** One entry of the conversation as the panel shows it. */
export type ThreadItem =
  | {
      kind: 'user';
      id: string;
      text: string;
      /** When it was sent (ms), null when unknown */
      at: number | null;
    }
  | {
      kind: 'assistant';
      id: string;
      text: string;
      /** When it arrived (ms), null when unknown */
      at: number | null;
      cards: AssistantCard[];
      links: AssistantLink[];
      pendingAction: PendingAction | null;
      toolsUsed: string[];
    }
  | {
      kind: 'error';
      id: string;
      /** The question to send again */
      retryText: string;
      title: string;
      message: string;
      retryable: boolean;
    };
