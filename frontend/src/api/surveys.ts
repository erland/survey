export type QuestionType = 'TEXT' | 'YES_NO' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'SCALE'
export type SurveyStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export type SurveyRunStatus = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'CLOSED'
export interface SurveyRunView {
  id: string
  surveyId: string
  publicId: string
  joinCode: string
  title: string
  status: SurveyRunStatus
  opensAt: string | null
  closesAt: string | null
  createdAt: string
  openedAt: string | null
  closedAt: string | null
}

export interface OptionInput { value: string; label: string }
export interface QuestionInput {
  type: QuestionType
  text: string
  required: boolean
  scaleMin: number | null
  scaleMax: number | null
  scaleMinLabel: string | null
  scaleMaxLabel: string | null
  options: OptionInput[]
}
export interface SurveyInput {
  title: string
  description: string
  status: SurveyStatus
  questions: QuestionInput[]
}
export interface SurveySummary {
  id: string
  title: string
  description: string | null
  status: SurveyStatus
  createdAt: string
  updatedAt: string
  questionCount: number
}
export interface SurveyView extends SurveyInput {
  id: string
  createdAt: string
  updatedAt: string
  version: number
  questions: Array<QuestionInput & { id: string; position: number; options: Array<OptionInput & { id: string; position: number }> }>
}

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) { super(message) }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (init?.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(url, {
    credentials: 'include',
    ...init,
    headers,
  })
  if (!response.ok) {
    let body: { code?: string; message?: string } = {}
    try { body = await response.json() } catch { /* ignore */ }
    throw new ApiError(response.status, body.code ?? 'HTTP_ERROR', body.message ?? `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const authApi = {
  me: () => request<{ authenticated: boolean; username: string }>('/api/auth/me'),
  login: (username: string, password: string) => request<{ username: string; expiresAt: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
}


export interface PublicRunLookup {
  publicId: string
  joinCode: string
  title: string
  status: SurveyRunStatus
  opensAt: string | null
  closesAt: string | null
  questionCount: number
}

export const publicRunApi = {
  byJoinCode: (joinCode: string) => request<PublicRunLookup>(`/api/public/runs/join/${encodeURIComponent(joinCode.trim().toUpperCase())}`),
}

export interface RunLiveSummary { started: number; active: number; submitted: number }

export interface ChoiceCount { value: string; label: string; count: number }
export interface ScaleCount { value: number; count: number }
export interface TextResultValue { text: string; updatedAt: string }
export interface QuestionResult {
  questionId: string
  position: number
  type: QuestionType
  text: string
  responseCount: number
  yesCount: number | null
  noCount: number | null
  choices: ChoiceCount[]
  scale: ScaleCount[]
  texts: TextResultValue[]
}


export interface PresentationTokenView {
  id: string
  token: string
  expiresAt: string
  presentationPath: string
}

export interface PresentationViewData {
  runId: string
  title: string
  status: SurveyRunStatus
  summary: RunLiveSummary
  results: QuestionResult[]
}

export const runApi = {
  list: (surveyId: string) => request<SurveyRunView[]>(`/api/admin/surveys/${surveyId}/runs`),
  create: (surveyId: string, title?: string) => request<SurveyRunView>(`/api/admin/surveys/${surveyId}/runs`, { method: 'POST', body: JSON.stringify({ title: title ?? null }) }),
  get: (runId: string) => request<SurveyRunView>(`/api/admin/runs/${runId}`),
  open: (runId: string) => request<SurveyRunView>(`/api/admin/runs/${runId}/open`, { method: 'POST' }),
  summary: (runId: string) => request<RunLiveSummary>(`/api/admin/runs/${runId}/summary`),
  results: (runId: string) => request<QuestionResult[]>(`/api/admin/runs/${runId}/results`),
  eventsUrl: (runId: string) => `/api/admin/runs/${runId}/events`,
  createPresentationToken: (runId: string) => request<PresentationTokenView>(`/api/admin/runs/${runId}/presentation-tokens`, { method: 'POST' }),
  revokePresentationToken: (runId: string, tokenId: string) => request<void>(`/api/admin/runs/${runId}/presentation-tokens/${tokenId}`, { method: 'DELETE' }),
  exportResultsJson: (runId: string) => downloadFile(`/api/admin/runs/${runId}/export/json`, 'survey-results.json'),
  exportResultsCsv: (runId: string) => downloadFile(`/api/admin/runs/${runId}/export/csv`, 'survey-results.csv'),
  exportPackage: (runId: string) => downloadFile(`/api/admin/runs/${runId}/export/package`, 'survey-package.zip'),
}

export const presentationApi = {
  view: (token: string) => request<PresentationViewData>(`/api/presentation/${encodeURIComponent(token)}`),
  eventsUrl: (token: string) => `/api/presentation/${encodeURIComponent(token)}/events`,
}

async function downloadFile(url: string, fallbackFilename = 'export.bin'): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(url, { credentials: 'include' })
  if (!response.ok) {
    let body: { code?: string; message?: string } = {}
    try { body = await response.json() } catch { /* ignore */ }
    throw new ApiError(response.status, body.code ?? 'HTTP_ERROR', body.message ?? `HTTP ${response.status}`)
  }
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const match = disposition.match(/filename=\"?([^\";]+)\"?/i)
  return { blob: await response.blob(), filename: match?.[1] ?? fallbackFilename }
}

export const surveyApi = {
  list: () => request<SurveySummary[]>('/api/admin/surveys'),
  get: (id: string) => request<SurveyView>(`/api/admin/surveys/${id}`),
  create: (input: SurveyInput) => request<SurveyView>('/api/admin/surveys', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: string, input: SurveyInput) => request<SurveyView>(`/api/admin/surveys/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  copy: (id: string) => request<SurveyView>(`/api/admin/surveys/${id}/copy`, { method: 'POST' }),
  remove: (id: string) => request<void>(`/api/admin/surveys/${id}`, { method: 'DELETE' }),
  exportDefinition: (id: string) => downloadFile(`/api/admin/surveys/${id}/export`, 'survey-definition.json'),
  importDefinition: (document: unknown) => request<SurveyView>('/api/admin/surveys/import', { method: 'POST', body: JSON.stringify(document) }),
}

export function emptyQuestion(type: QuestionType = 'TEXT'): QuestionInput {
  return {
    type,
    text: '',
    required: false,
    scaleMin: type === 'SCALE' ? 1 : null,
    scaleMax: type === 'SCALE' ? 5 : null,
    scaleMinLabel: null,
    scaleMaxLabel: null,
    options: type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE'
      ? [{ value: 'option-1', label: 'Alternativ 1' }, { value: 'option-2', label: 'Alternativ 2' }]
      : [],
  }
}

export interface ParticipantSessionView {
  sessionId: string
  participantToken: string
  status: 'ACTIVE' | 'SUBMITTED' | 'EXPIRED'
  startedAt: string
  lastActivityAt: string
  resumed: boolean
}

export const participantApi = {
  create: (publicId: string) => request<ParticipantSessionView>(`/api/public/runs/${publicId}/participants`, { method: 'POST' }),
  resume: (publicId: string, token: string) => request<ParticipantSessionView>(`/api/public/runs/${publicId}/participants/current`, { headers: { 'X-Participant-Token': token } }),
  heartbeat: (publicId: string, token: string) => request<{ lastActivityAt: string }>(`/api/public/runs/${publicId}/participants/current/heartbeat`, { method: 'POST', headers: { 'X-Participant-Token': token } }),
  survey: (publicId: string, token: string) => request<ParticipantSurveyView>(`/api/public/runs/${publicId}/participants/current/survey`, { headers: { 'X-Participant-Token': token } }),
  responses: (publicId: string, token: string) => request<{ answers: ParticipantAnswerView[] }>(`/api/public/runs/${publicId}/participants/current/responses`, { headers: { 'X-Participant-Token': token } }),
  saveResponse: (publicId: string, questionId: string, token: string, answer: SaveParticipantAnswer) => request<ParticipantAnswerView>(`/api/public/runs/${publicId}/participants/current/responses/${questionId}`, { method: 'PUT', headers: { 'X-Participant-Token': token }, body: JSON.stringify(answer) }),
  submit: (publicId: string, token: string) => request<ParticipantSubmitResult>(`/api/public/runs/${publicId}/participants/current/submit`, { method: 'POST', headers: { 'X-Participant-Token': token } }),
}


export interface ParticipantOptionView {
  id: string
  position: number
  value: string
  label: string
}

export interface ParticipantQuestionView {
  id: string
  position: number
  type: QuestionType
  text: string
  required: boolean
  scaleMin: number | null
  scaleMax: number | null
  scaleMinLabel: string | null
  scaleMaxLabel: string | null
  options: ParticipantOptionView[]
}

export interface ParticipantSurveyView {
  publicId: string
  title: string
  questions: ParticipantQuestionView[]
}

export interface ParticipantAnswerView {
  questionId: string
  textValue: string | null
  booleanValue: boolean | null
  numericValue: number | null
  optionValues: string[]
  updatedAt: string
}

export interface SaveParticipantAnswer {
  textValue: string | null
  booleanValue: boolean | null
  numericValue: number | null
  optionValues: string[]
}

export interface ParticipantSubmitResult {
  status: 'SUBMITTED'
  submittedAt: string
  alreadySubmitted: boolean
}
