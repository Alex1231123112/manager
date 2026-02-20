export interface TeamDto {
  id: number;
  name: string;
  telegramChatId: string | null;
  channelTelegramChatId: string | null;
}

export interface PlayerDto {
  id: number;
  name: string;
  number: number | null;
  status: string;
  debt: number | string;
}

export interface MatchDto {
  id: number;
  opponent: string;
  date: string | null;
  ourScore: number | null;
  opponentScore: number | null;
  location: string | null;
  status: string;
}

export interface MatchStatRowDto {
  playerId: number;
  playerName: string;
  number: number | null;
  minutes: number | null;
  points: number;
  rebounds: number;
  assists: number;
  fouls: number;
  plusMinus: number | null;
  mvp: boolean;
}

export interface MatchStatsDto {
  stats: MatchStatRowDto[];
  mvpPlayerName: string | null;
}

export interface MeResponse {
  teams: TeamDto[];
  currentTeam: TeamDto | null;
}

export interface DashboardDto {
  team: TeamDto;
  playerCount: number;
  debtorCount: number;
  totalDebt: number;
  nextMatch: MatchDto | null;
}

export interface ActionResult {
  success: boolean;
  message: string | null;
  data: string | null;
}

export interface DebtListDto {
  debtors: PlayerDto[];
}

export interface FinanceEntryDto {
  id: number;
  type: string;
  amount: number | string;
  description: string | null;
  entryDate: string;
}

export interface FinanceReportDto {
  from: string;
  to: string;
  totalIncome: number | string;
  totalExpense: number | string;
  balance: number | string;
  entries: FinanceEntryDto[];
}

export interface EventDto {
  id: number;
  title: string;
  eventType: string;
  eventDate: string;
  location: string | null;
  description: string | null;
}

export interface EventDto {
  id: number;
  title: string;
  eventType: string;
  eventDate: string;
  location: string | null;
  description: string | null;
}

export interface SettingsDto {
  channelId: string;
  groupChatId?: string;
}

export interface SystemSettingsDto {
  adminTelegramId: string;
  adminTelegramUsername: string;
}

export interface MemberDto {
  telegramUserId: string;
  telegramUsername: string;
  displayName: string;
  role: string;
  number: number | null;
  status: string;
  debt: number | string;
  isActive: boolean;
}

export interface InvitationDto {
  code: string;
  link: string;
  role: string;
  expiresAt: string;
}

export interface InvitationCreateRequest {
  role?: string;
  expiresInDays?: number;
}

export interface InvitationCreateResponse {
  success: boolean;
  invitation: InvitationDto | null;
  error: string | null;
}

export interface LeagueTableRowDto {
  id: number;
  position: number;
  teamName: string;
  wins: number;
  losses: number;
  pointsDiff: number;
}

export interface LeagueTableRowCreateDto {
  position?: number;
  teamName: string;
  wins?: number;
  losses?: number;
  pointsDiff?: number;
}

/** Состав матча по подтверждениям */
export interface MatchAttendanceRowDto {
  telegramUserId: string;
  displayName: string;
  telegramUsername: string;
  status?: string;
}

export interface MatchAttendanceDto {
  responded: MatchAttendanceRowDto[];
  noResponse: MatchAttendanceRowDto[];
}

/** Подтверждения участника по матчам (для профиля) */
export interface MemberAttendanceDto {
  matchId: number;
  opponent: string;
  date: string | null;
  status: string | null;
}

/** Метрики интеграции за период */
export interface IntegrationStatsDto {
  from: string;
  to: string;
  total: number;
  success: number;
  failed: number;
  byType: Array<{ eventType: string; label: string; success: number; failed: number }>;
}

/** Одно событие интеграции */
export interface IntegrationEventDto {
  id: number;
  eventType: string;
  targetChatId: string | null;
  success: boolean;
  errorMessage: string | null;
  teamId: number | null;
  matchId: number | null;
  createdAt: string | null;
}
