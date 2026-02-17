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

export interface SettingsDto {
  channelId: string;
}

export interface SystemSettingsDto {
  adminTelegramUsername: string;
}

export interface MemberDto {
  telegramUserId: string;
  telegramUsername: string;
  displayName: string;
  role: string;
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
