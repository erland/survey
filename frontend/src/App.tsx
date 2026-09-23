import { useEffect, useMemo, useRef, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { accountApi, ApiError, authApi, emptyQuestion, participantApi, ParticipantSurveyView, presentationApi, publicRunApi, QuestionInput, QuestionResult, QuestionType, RunLiveSummary, runApi, SurveyAccountMembership, SurveyInput, SurveyRunView, SurveySummary, SurveyView, surveyApi, systemApi } from './api/surveys'

type Screen = { kind: 'list' } | { kind: 'edit'; id: string | null } | { kind: 'admins' } | { kind: 'system' } | { kind: 'password'; returnTo: 'list' | 'system' }

const typeLabels: Record<QuestionType, string> = {
  TEXT: 'Fritext', YES_NO: 'Ja / nej', SINGLE_CHOICE: 'Vallista', MULTIPLE_CHOICE: 'Kryssrutor', SCALE: 'Skala',
}


function JoinLanding() {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function join(e: React.FormEvent) {
    e.preventDefault()
    const normalized = code.replace(/\s+/g, '').toUpperCase()
    if (!normalized) { setError('Ange koden som visas av workshopledaren.'); return }
    setBusy(true); setError('')
    try {
      const run = await publicRunApi.byJoinCode(normalized)
      window.location.assign(`/r/${encodeURIComponent(run.publicId)}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Kunde inte hitta en aktiv enkät med den koden.')
      setBusy(false)
    }
  }
  return <main className="join-page">
    <section className="join-card card">
      <p className="eyebrow">Survey Service</p>
      <h1>Delta i en enkät</h1>
      <p className="muted">Skriv koden som visas på skärmen. Du behöver inget konto.</p>
      <form onSubmit={join} className="join-form">
        <label htmlFor="join-code">Kod</label>
        <input id="join-code" className="join-code-input" value={code} onChange={e=>setCode(e.target.value.toUpperCase())} autoCapitalize="characters" autoCorrect="off" spellCheck={false} inputMode="text" placeholder="K7M4QX" maxLength={12} autoFocus />
        {error && <div className="error" role="alert">{error}</div>}
        <button className="primary join-button" disabled={busy}>{busy ? 'Ansluter…' : 'Öppna enkäten'}</button>
      </form>
      <p className="join-privacy">Deltagandet är anonymt i standardläget. Tjänsten frågar inte efter namn eller e-post.</p>
      <a className="admin-link" href="/admin">Administratör</a>
    </section>
  </main>
}

function Login({ onLoggedIn }: { onLoggedIn: (username: string) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError('')
    try { const r = await authApi.login(username, password); onLoggedIn(r.username) }
    catch (e) { setError(e instanceof Error ? e.message : 'Inloggningen misslyckades.') }
    finally { setBusy(false) }
  }
  return <main className="center-shell"><form className="card login-card" onSubmit={submit}>
    <p className="eyebrow">Survey Service</p><h1>Administratör</h1><p className="muted">Logga in för att skapa och hantera enkäter.</p>
    <label>E-post eller systemadmin<input value={username} onChange={e=>setUsername(e.target.value)} autoComplete="username" /></label>
    <label>Lösenord<input type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete="current-password" /></label>
    {error && <div className="error" role="alert">{error}</div>}
    <button className="primary" disabled={busy}>{busy ? 'Loggar in…' : 'Logga in'}</button>
  </form></main>
}


function SetPassword({ token }: { token:string|null }) {
  const [password,setPassword]=useState('')
  const [confirmPassword,setConfirmPassword]=useState('')
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const [done,setDone]=useState(false)

  async function submit(e:React.FormEvent){
    e.preventDefault()
    setError('')
    if(!token){setError('Lösenordslänken saknar token.');return}
    if(password.length<8){setError('Lösenordet måste vara minst 8 tecken.');return}
    if(password!==confirmPassword){setError('Lösenorden matchar inte.');return}
    setBusy(true)
    try{await authApi.setPassword(token,password);setDone(true)}
    catch(e){setError(e instanceof Error?e.message:'Kunde inte sätta lösenordet.')}
    finally{setBusy(false)}
  }

  if(done) return <main className="center-shell"><section className="card login-card">
    <p className="eyebrow">Survey Service</p><h1>Lösenordet är sparat</h1>
    <p>Du kan nu logga in med din e-postadress och det nya lösenordet.</p>
    <a href="/admin">Gå till inloggningen</a>
  </section></main>

  return <main className="center-shell"><form className="card login-card" onSubmit={submit}>
    <p className="eyebrow">Survey Service</p><h1>Sätt lösenord</h1>
    <p className="muted">Länken kan bara användas en gång. Lösenordet måste vara minst 8 tecken.</p>
    <label>Nytt lösenord<input type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete="new-password" required /></label>
    <label>Upprepa lösenord<input type="password" value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} autoComplete="new-password" required /></label>
    {error&&<div className="error" role="alert">{error}</div>}
    <button className="primary" disabled={busy||!token}>{busy?'Sparar…':'Sätt lösenord'}</button>
  </form></main>
}

function ChangePassword({ onDone }: { onDone:()=>void }) {
  const [currentPassword,setCurrentPassword]=useState('')
  const [newPassword,setNewPassword]=useState('')
  const [confirmPassword,setConfirmPassword]=useState('')
  const [error,setError]=useState('')
  const [done,setDone]=useState(false)
  const [busy,setBusy]=useState(false)

  async function submit(e:React.FormEvent){
    e.preventDefault()
    setError('')
    if(newPassword.length<8){setError('Det nya lösenordet måste vara minst 8 tecken.');return}
    if(newPassword!==confirmPassword){setError('De nya lösenorden matchar inte.');return}
    setBusy(true)
    try{
      await authApi.changePassword(currentPassword,newPassword)
      setCurrentPassword('');setNewPassword('');setConfirmPassword('');setDone(true)
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte ändra lösenordet.')}
    finally{setBusy(false)}
  }

  return <section>
    <div className="page-heading"><div><button className="back" onClick={onDone}>← Tillbaka</button><p className="eyebrow">Konto</p><h1>Ändra lösenord</h1><p className="muted">Ange ditt nuvarande lösenord och välj ett nytt lösenord med minst 8 tecken.</p></div></div>
    <form className="card login-card" onSubmit={submit}>
      <label>Nuvarande lösenord<input type="password" value={currentPassword} onChange={e=>setCurrentPassword(e.target.value)} autoComplete="current-password" required /></label>
      <label>Nytt lösenord<input type="password" value={newPassword} onChange={e=>setNewPassword(e.target.value)} autoComplete="new-password" required /></label>
      <label>Upprepa nytt lösenord<input type="password" value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} autoComplete="new-password" required /></label>
      {error&&<div className="error" role="alert">{error}</div>}
      {done&&<div role="status">Lösenordet är ändrat. Övriga inloggade sessioner har avslutats.</div>}
      <button className="primary" disabled={busy}>{busy?'Sparar…':'Ändra lösenord'}</button>
    </form>
  </section>
}

function InviteLink({ path, expiresAt, title='Länk för första lösenordet' }: { path:string; expiresAt:string|null; title?:string }) {
  const url=window.location.origin+path
  const [copied,setCopied]=useState(false)
  async function copy(){
    await navigator.clipboard.writeText(url)
    setCopied(true)
  }
  return <div className="card">
    <h3>{title}</h3>
    <p className="muted">Skicka länken till administratören. Den kan användas en gång{expiresAt?` och gäller till ${new Date(expiresAt).toLocaleString()}`:''}.</p>
    <label>Länk<input readOnly value={url} onFocus={e=>e.currentTarget.select()} /></label>
    <button type="button" onClick={()=>void copy()}>{copied?'Kopierad':'Kopiera länk'}</button>
  </div>
}

function AccountChooser({ accounts, onChoose, systemAdmin, onSystem, onPassword }: { accounts: SurveyAccountMembership[]; onChoose: (id:string)=>void; systemAdmin:boolean; onSystem:()=>void; onPassword:()=>void }) {
  return <main className="center-shell"><section className="card account-chooser">
    <p className="eyebrow">Survey Service</p>
    <h1>Välj enkätkonto</h1>
    {accounts.length === 0
      ? <><p>Du är inte kopplad till något enkätkonto.</p><p className="muted">Be en administratör lägga till dig på ett konto.</p></>
      : <div className="account-choice-list">{accounts.map(account =>
          <button className="account-choice" key={account.id} onClick={()=>onChoose(account.id)}>
            <strong>{account.name}</strong><span>{account.role}</span>
          </button>)}</div>}
    <div className="system-entry"><button onClick={onPassword}>Ändra lösenord</button>{systemAdmin && <button onClick={onSystem}>Systemadministration</button>}</div>
  </section></main>
}

function AccountAdminPanel({ accountId, onDone }: { accountId:string; onDone:()=>void }) {
  const [admins,setAdmins]=useState<Awaited<ReturnType<typeof accountApi.admins>>>([])
  const [username,setUsername]=useState('')
  const [invite,setInvite]=useState<{path:string;expiresAt:string|null}|null>(null)
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  async function load(){ try{ setAdmins(await accountApi.admins(accountId)); setError('') }catch(e){ setError(e instanceof Error?e.message:'Kunde inte läsa administratörer.') } }
  useEffect(()=>{ void load() },[accountId])
  async function add(e:React.FormEvent){
    e.preventDefault();setBusy(true);setError('');setInvite(null)
    try{
      const created=await accountApi.addAdmin(accountId,username)
      setUsername('')
      if(created.initialPasswordPath) setInvite({path:created.initialPasswordPath,expiresAt:created.initialPasswordExpiresAt})
      await load()
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte lägga till administratören.')}
    finally{setBusy(false)}
  }
  async function remove(userId:string, name:string){ if(!confirm(`Ta bort ${name} från enkätkontot?`))return;setError('');try{await accountApi.removeAdmin(accountId,userId);await load()}catch(e){setError(e instanceof Error?e.message:'Kunde inte ta bort administratören.')}}
  return <section>
    <div className="page-heading"><div><button className="back" onClick={onDone}>← Enkäter</button><p className="eyebrow">Enkätkonto</p><h1>Administratörer</h1><p className="muted">Hantera vilka administratörer som får arbeta i detta enkätkonto.</p></div></div>
    {error&&<div className="error" role="alert">{error}</div>}
    <div className="card admin-management">
      <h2>Lägg till administratör</h2>
      <p className="muted">Nya administratörer får en engångslänk där de själva sätter sitt första lösenord.</p>
      <form onSubmit={add} className="admin-add-form">
        <label>E-postadress<input type="email" value={username} onChange={e=>setUsername(e.target.value)} required /></label>
        <button className="primary" disabled={busy||!username.trim()}>{busy?'Lägger till…':'Lägg till'}</button>
      </form>
    </div>
    {invite&&<InviteLink path={invite.path} expiresAt={invite.expiresAt}/>}
    <div className="card">
      <h2>Befintliga administratörer</h2>
      <div className="admin-list">{admins.map(admin=><div className="admin-row" key={admin.userId}><div><strong>{admin.username}</strong><span className="muted"> {admin.role}</span></div><button className="danger-ghost" onClick={()=>void remove(admin.userId,admin.username)}>Ta bort</button></div>)}</div>
    </div>
  </section>
}

function SystemAccountPanel({ onDone, onChanged }: { onDone:()=>void; onChanged:()=>Promise<void> }) {
  const [accounts,setAccounts]=useState<Awaited<ReturnType<typeof systemApi.accounts>>>([])
  const [admins,setAdmins]=useState<Awaited<ReturnType<typeof systemApi.admins>>>([])
  const [name,setName]=useState('')
  const [adminUsername,setAdminUsername]=useState('')
  const [invite,setInvite]=useState<{path:string;expiresAt:string|null}|null>(null)
  const [resetLink,setResetLink]=useState<{path:string;expiresAt:string|null;username:string}|null>(null)
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)

  async function load(){
    try{
      const [accountItems,adminItems]=await Promise.all([systemApi.accounts(),systemApi.admins()])
      setAccounts(accountItems);setAdmins(adminItems);setError('')
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte läsa systemadministrationen.')}
  }

  useEffect(()=>{void load()},[])

  async function create(e:React.FormEvent){
    e.preventDefault();setBusy(true);setError('');setInvite(null)
    try{
      const created=await systemApi.createAccount(name,adminUsername)
      setName('');setAdminUsername('')
      await load()
      if(created.initialPasswordPath) setInvite({path:created.initialPasswordPath,expiresAt:created.initialPasswordExpiresAt})
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte skapa enkätkontot.')}
    finally{setBusy(false)}
  }

  async function createPasswordResetLink(userId:string, username:string){
    setError('');setResetLink(null)
    try{
      const link=await systemApi.createPasswordResetLink(userId)
      setResetLink({path:link.path,expiresAt:link.expiresAt,username})
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte skapa återställningslänken.')}
  }

  async function changeAdmin(userId:string, action:'activate'|'deactivate'){
    setError('')
    try{
      if(action==='activate') await systemApi.activateAdmin(userId)
      else await systemApi.deactivateAdmin(userId)
      await load()
    }catch(e){setError(e instanceof Error?e.message:'Kunde inte ändra administratörskontot.')}
  }

  async function deleteAdmin(userId:string,username:string){
    if(!confirm(`Ta bort administratörskontot "${username}" permanent?`)) return
    setError('')
    try{await systemApi.deleteAdmin(userId);await load()}
    catch(e){setError(e instanceof Error?e.message:'Kunde inte ta bort administratörskontot.')}
  }

  return <section>
    <div className="page-heading"><div><button className="back" onClick={onDone}>← Tillbaka</button><p className="eyebrow">Systemadministration</p><h1>Enkätkonton</h1></div></div>
    {error&&<div className="error" role="alert">{error}</div>}
    <div className="card admin-management"><h2>Skapa enkätkonto</h2><p className="muted">Om den första administratören är ny skapas en engångslänk för att sätta lösenordet.</p><form onSubmit={create} className="admin-add-form">
      <label>Kontonamn<input value={name} onChange={e=>setName(e.target.value)} required /></label>
      <label>Första administratörens e-post<input type="email" value={adminUsername} onChange={e=>setAdminUsername(e.target.value)} required /></label>
      <button className="primary" disabled={busy||!name.trim()||!adminUsername.trim()}>{busy?'Skapar…':'Skapa konto'}</button>
    </form></div>
    {invite&&<InviteLink path={invite.path} expiresAt={invite.expiresAt}/>}
    {resetLink&&<InviteLink path={resetLink.path} expiresAt={resetLink.expiresAt} title={`Återställ lösenord för ${resetLink.username}`}/>}

    <div className="card"><h2>Alla enkätkonton</h2><div className="admin-list">{accounts.map(account=><div className="admin-row" key={account.id}><div><strong>{account.name}</strong><div className="muted">{account.adminCount} administratör{account.adminCount===1?'':'er'}</div></div></div>)}</div></div>

    <div className="card"><h2>Administratörskonton</h2><p className="muted">Konton utan enkätkonto-medlemskap kan tas bort permanent efter att de har inaktiverats. Konton med historiska referenser kan behöva behållas.</p>
      <div className="admin-list">{admins.map(admin=><div className="admin-row" key={admin.id}>
        <div>
          <strong>{admin.username}</strong>
          <div className="muted">{admin.systemAdmin?'Systemadmin · ':''}{admin.active?'Aktiv':'Inaktiv'} · {admin.accountCount} enkätkonto{admin.accountCount===1?'':'n'}</div>
        </div>
        <div className="actions">
          {!admin.systemAdmin && admin.active && <button onClick={()=>void createPasswordResetLink(admin.id,admin.username)}>Skapa återställningslänk</button>}
          {!admin.systemAdmin && (admin.active
            ? <button onClick={()=>void changeAdmin(admin.id,'deactivate')}>Inaktivera</button>
            : <button onClick={()=>void changeAdmin(admin.id,'activate')}>Återaktivera</button>)}
          {!admin.systemAdmin && !admin.active && admin.accountCount===0 && <button className="danger-ghost" onClick={()=>void deleteAdmin(admin.id,admin.username)}>Ta bort</button>}
        </div>
      </div>)}</div>
    </div>
  </section>
}
function SurveyList({ accountId, onEdit }: { accountId:string; onEdit: (id: string | null) => void }) {
  const [items, setItems] = useState<SurveySummary[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)
  const importRef = useRef<HTMLInputElement>(null)
  async function load() { setBusy(true); setError(''); try { setItems(await surveyApi.list(accountId)) } catch(e){ setError(e instanceof Error ? e.message : 'Kunde inte läsa enkäter.') } finally { setBusy(false) } }
  useEffect(()=>{ void load() },[accountId])
  async function copy(id: string) { try { await surveyApi.copy(accountId,id); await load() } catch(e){ setError(e instanceof Error ? e.message : 'Kopiering misslyckades.') } }
  async function remove(id: string, title: string) { if (!confirm(`Radera "${title}" permanent? Alla genomföranden och insamlade svar för enkäten tas också bort.`)) return; try { await surveyApi.remove(accountId,id); await load() } catch(e){ setError(e instanceof Error ? e.message : 'Radering misslyckades.') } }
  async function importFile(file: File | undefined) {
    if (!file) return
    setError('')
    try {
      const document = JSON.parse(await file.text()) as unknown
      const created = await surveyApi.importDefinition(accountId,document)
      await load()
      onEdit(created.id)
    } catch(e) {
      if (e instanceof SyntaxError) setError('Filen är inte giltig JSON.')
      else setError(e instanceof Error ? e.message : 'Importen misslyckades.')
    } finally {
      if (importRef.current) importRef.current.value = ''
    }
  }
  return <section>
    <div className="page-heading"><div><p className="eyebrow">Enkäter</p><h1>Mina enkäter</h1><p className="muted">Skapa mallar som senare kan användas i flera workshopgenomföranden.</p></div><div className="actions"><input ref={importRef} type="file" accept="application/json,.json" hidden onChange={e=>void importFile(e.target.files?.[0])}/><button onClick={()=>importRef.current?.click()}>Importera JSON</button><button className="primary" onClick={()=>onEdit(null)}>+ Ny enkät</button></div></div>
    {error && <div className="error" role="alert">{error}</div>}
    {busy ? <div className="card">Läser in…</div> : items.length === 0 ? <div className="card empty"><h2>Ingen enkät ännu</h2><p>Börja med att skapa din första enkät.</p><button className="primary" onClick={()=>onEdit(null)}>Skapa enkät</button></div> :
      <div className="survey-grid">{items.map(s=><article className="card survey-card" key={s.id}>
        <div className="survey-card-main"><span className="badge">{s.status}</span><h2>{s.title}</h2><p className="muted">{s.description || 'Ingen beskrivning'}</p><p className="meta">{s.questionCount} frågor · Uppdaterad {new Date(s.updatedAt).toLocaleDateString('sv-SE')}</p></div>
        <div className="actions"><button onClick={()=>onEdit(s.id)}>Öppna</button><button onClick={()=>void copy(s.id)}>Kopiera</button><button className="danger-ghost" onClick={()=>void remove(s.id,s.title)}>Radera</button></div>
      </article>)}</div>}
  </section>
}

function QuestionEditor({ q, index, total, onChange, onMove, onRemove }: { q: QuestionInput; index:number; total:number; onChange:(q:QuestionInput)=>void; onMove:(delta:number)=>void; onRemove:()=>void }) {
  function changeType(type: QuestionType) {
    const base = emptyQuestion(type)
    onChange({ ...base, text: q.text, required: q.required })
  }
  function updateOption(i:number, patch:Partial<{value:string;label:string}>) { const options=q.options.map((o,j)=>j===i?{...o,...patch}:o); onChange({...q,options}) }
  function addOption(){ const n=q.options.length+1; onChange({...q, options:[...q.options,{value:`option-${n}`,label:`Alternativ ${n}`}]}) }
  function removeOption(i:number){ onChange({...q,options:q.options.filter((_,j)=>j!==i)}) }
  return <article className="question-card">
    <div className="question-head"><div className="question-number">{index+1}</div><select aria-label="Frågetyp" value={q.type} onChange={e=>changeType(e.target.value as QuestionType)}>{Object.entries(typeLabels).map(([v,l])=><option key={v} value={v}>{l}</option>)}</select><div className="spacer"/><button className="icon" disabled={index===0} onClick={()=>onMove(-1)} aria-label="Flytta upp">↑</button><button className="icon" disabled={index===total-1} onClick={()=>onMove(1)} aria-label="Flytta ned">↓</button><button className="danger-ghost" onClick={onRemove}>Ta bort</button></div>
    <label>Fråga<textarea rows={2} value={q.text} onChange={e=>onChange({...q,text:e.target.value})} placeholder="Skriv frågan här…" /></label>
    <label className="checkbox"><input type="checkbox" checked={q.required} onChange={e=>onChange({...q,required:e.target.checked})}/><span>Obligatorisk fråga</span></label>
    {(q.type==='SINGLE_CHOICE'||q.type==='MULTIPLE_CHOICE') && <div className="options"><strong>Svarsalternativ</strong>{q.options.map((o,i)=><div className="option-row" key={i}><input aria-label={`Alternativ ${i+1}`} value={o.label} onChange={e=>updateOption(i,{label:e.target.value,value:slug(e.target.value)||`option-${i+1}`})}/><button className="icon" onClick={()=>removeOption(i)} disabled={q.options.length<=2}>×</button></div>)}<button onClick={addOption}>+ Lägg till alternativ</button></div>}
    {q.type==='SCALE' && <div className="scale-grid"><label>Från<input type="number" value={q.scaleMin ?? 1} onChange={e=>onChange({...q,scaleMin:Number(e.target.value)})}/></label><label>Till<input type="number" value={q.scaleMax ?? 5} onChange={e=>onChange({...q,scaleMax:Number(e.target.value)})}/></label><label>Etikett vänster<input value={q.scaleMinLabel ?? ''} onChange={e=>onChange({...q,scaleMinLabel:e.target.value||null})} placeholder="Inte alls"/></label><label>Etikett höger<input value={q.scaleMaxLabel ?? ''} onChange={e=>onChange({...q,scaleMaxLabel:e.target.value||null})} placeholder="Mycket"/></label></div>}
  </article>
}
function slug(value:string){ return value.trim().toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'') }

function validate(input: SurveyInput): string[] {
  const errors:string[]=[]
  if(!input.title.trim()) errors.push('Enkäten måste ha en titel.')
  input.questions.forEach((q,i)=>{
    if(!q.text.trim()) errors.push(`Fråga ${i+1} saknar frågetext.`)
    if((q.type==='SINGLE_CHOICE'||q.type==='MULTIPLE_CHOICE') && q.options.length<2) errors.push(`Fråga ${i+1} behöver minst två svarsalternativ.`)
    if(q.type==='SCALE' && (q.scaleMin==null||q.scaleMax==null||q.scaleMin>=q.scaleMax)) errors.push(`Fråga ${i+1} har en ogiltig skala.`)
  })
  return errors
}

function ResultBars({ items, responseCount }: { items: Array<{ label: string; count: number }>; responseCount: number }) {
  const max = Math.max(1, ...items.map(item => item.count))
  return <div className="result-bars" role="group" aria-label="Resultatfördelning"><ul className="sr-only">{items.map(item => {
    const percent = responseCount > 0 ? Math.round((item.count / responseCount) * 100) : 0
    return <li key={`text-${item.label}`}>{item.label}: {item.count} svar, {percent} procent</li>
  })}</ul>{items.map(item => {
    const width = `${Math.round((item.count / max) * 100)}%`
    const percent = responseCount > 0 ? Math.round((item.count / responseCount) * 100) : 0
    return <div className="result-bar-row" key={item.label}>
      <div className="result-bar-label"><span>{item.label}</span><strong>{item.count}</strong></div>
      <div className="result-bar-track" aria-hidden="true">
        <div className="result-bar-fill" style={{ width }} />
      </div>
      <span className="result-percent" aria-hidden="true">{percent}%</span>
    </div>
  })}</div>
}


function TextResultList({ texts }: { texts: QuestionResult['texts'] }) {
  if (texts.length === 0) return <p className="muted">Inga fritextsvar ännu.</p>
  return <ol className="text-result-list" aria-label="Fritextsvar">
    {texts.map((value, index) => <li className="text-result-item" key={`${value.updatedAt}-${index}`}>
      <p>{value.text}</p>
      <time dateTime={value.updatedAt}>{new Date(value.updatedAt).toLocaleString('sv-SE')}</time>
    </li>)}
  </ol>
}

function QuestionResultCard({ result }: { result: QuestionResult }) {
  let items: Array<{label:string; count:number}> = []
  if (result.type === 'YES_NO') items = [
    { label: 'Ja', count: result.yesCount ?? 0 },
    { label: 'Nej', count: result.noCount ?? 0 },
  ]
  if (result.type === 'SINGLE_CHOICE' || result.type === 'MULTIPLE_CHOICE') {
    items = result.choices.map(x => ({ label: x.label, count: x.count }))
  }
  if (result.type === 'SCALE') items = result.scale.map(x => ({ label: String(x.value), count: x.count }))
  return <article className="card result-card">
    <div className="result-card-heading"><div><span className="badge">{typeLabels[result.type]}</span><h3>{result.position + 1}. {result.text}</h3></div><span className="response-count">{result.responseCount} svar</span></div>
    {result.type === 'TEXT'
      ? <TextResultList texts={result.texts} />
      : <ResultBars items={items} responseCount={result.responseCount} />}
  </article>
}

function SharePanel({ accountId, surveyId, surveyTitle }: { accountId:string; surveyId: string; surveyTitle: string }) {
  const [runs, setRuns] = useState<SurveyRunView[]>([])
  const [selected, setSelected] = useState<SurveyRunView | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [summary, setSummary] = useState<RunLiveSummary | null>(null)
  const [results, setResults] = useState<QuestionResult[]>([])

  async function load() {
    try {
      const items = await runApi.list(accountId,surveyId)
      setRuns(items)
      if (!selected && items.length > 0) setSelected(items[0])
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Kunde inte läsa genomföranden.')
    }
  }
  useEffect(() => { void load() }, [accountId,surveyId])
  useEffect(() => {
    if (!selected) { setSummary(null); setResults([]); return }
    let cancelled = false
    let fallbackTimer: number | null = null
    const runId = selected.id

    async function refreshLiveData() {
      try {
        const [nextSummary, nextResults] = await Promise.all([runApi.summary(accountId,runId), runApi.results(accountId,runId)])
        if (!cancelled) { setSummary(nextSummary); setResults(nextResults) }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Kunde inte läsa deltagarstatus.')
      }
    }

    function stopFallback() {
      if (fallbackTimer != null) { window.clearInterval(fallbackTimer); fallbackTimer = null }
    }
    function startFallback() {
      if (fallbackTimer == null) fallbackTimer = window.setInterval(() => void refreshLiveData(), 5000)
    }

    void refreshLiveData()
    const events = new EventSource(runApi.eventsUrl(accountId,runId), { withCredentials: true })
    const onChange = () => void refreshLiveData()
    for (const type of ['connected', 'participant_started', 'participant_activity', 'response_updated', 'participant_submitted']) {
      events.addEventListener(type, onChange)
    }
    events.onopen = () => stopFallback()
    events.onerror = () => startFallback()

    return () => {
      cancelled = true
      stopFallback()
      events.close()
    }
  }, [accountId,selected?.id])

  async function startRun() {
    setBusy(true); setError('')
    try {
      const draft = await runApi.create(accountId,surveyId, surveyTitle)
      const opened = await runApi.open(accountId,draft.id)
      setSelected(opened)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Kunde inte starta genomförandet.')
    } finally { setBusy(false) }
  }

  async function copy(value: string) {
    try { await navigator.clipboard.writeText(value) }
    catch { setError('Kunde inte kopiera automatiskt. Markera och kopiera texten manuellt.') }
  }

  const directUrl = selected ? `${window.location.origin}/r/${selected.publicId}` : ''
  return <section className="share-section" id="workshop">
    <div className="section-heading"><div><p className="eyebrow">Workshop</p><h2>Dela enkäten</h2><p className="muted">Starta ett genomförande och visa länken, QR-koden eller kortkoden för deltagarna.</p></div><button className="primary" disabled={busy} onClick={()=>void startRun()}>{busy ? 'Startar…' : 'Starta nytt genomförande'}</button></div>
    {error && <div className="error" role="alert">{error}</div>}
    {runs.length > 0 && <label className="run-picker">Genomförande<select value={selected?.id ?? ''} onChange={e=>setSelected(runs.find(r=>r.id===e.target.value) ?? null)}>{runs.map(r=><option value={r.id} key={r.id}>{r.title} · {r.status} · {new Date(r.createdAt).toLocaleString('sv-SE')}</option>)}</select></label>}
    {selected && <>
      <div className="live-summary" aria-live="polite">
        <div className="live-stat"><strong>{summary?.started ?? '–'}</strong><span>har påbörjat</span></div>
        <div className="live-stat"><strong>{summary?.active ?? '–'}</strong><span>svarar just nu</span></div>
        <div className="live-stat"><strong>{summary?.submitted ?? '–'}</strong><span>klara</span></div>
      </div>
      <div className="share-card card">
      <div className="qr-wrap"><QRCodeSVG value={directUrl} size={220} marginSize={4} title={`QR-kod till ${selected.title}`} /></div>
      <div className="share-details"><span className={`run-status status-${selected.status.toLowerCase()}`}>{selected.status}</span><h3>{selected.title}</h3><div className="join-code" aria-label={`Anslutningskod ${selected.joinCode}`}>{selected.joinCode}</div><p className="muted">Deltagare kan använda kortkoden på tjänstens startsida eller öppna direktlänken.</p>
        <label>Direktlänk<div className="copy-row"><input readOnly value={directUrl}/><button onClick={()=>void copy(directUrl)}>Kopiera</button></div></label>
        <div className="copy-row"><span className="service-address">{window.location.origin}</span><button onClick={()=>void copy(selected.joinCode)}>Kopiera kod</button></div>
      </div>
    </div>
    <div className="presentation-launch"><button className="primary" onClick={()=>{
      const popup = window.open('', '_blank')
      void runApi.createPresentationToken(accountId,selected.id).then(created => {
        const url = `${window.location.origin}${created.presentationPath}`
        if (popup) { popup.opener = null; popup.location.href = url }
        else setError('Webbläsaren blockerade presentationsfönstret. Tillåt popup-fönster och försök igen.')
      }).catch(e => {
        popup?.close()
        setError(e instanceof Error ? e.message : 'Kunde inte skapa presentationslänk.')
      })
    }}>Öppna presentationsläge</button><button onClick={()=>{
      setError('')
      void runApi.exportResultsJson(accountId,selected.id).then(({blob,filename})=>{
        const url=URL.createObjectURL(blob)
        const anchor=document.createElement('a'); anchor.href=url; anchor.download=filename; anchor.click()
        URL.revokeObjectURL(url)
      }).catch(e=>setError(e instanceof Error?e.message:'Kunde inte exportera resultat.'))
    }}>Exportera resultat (JSON)</button><button onClick={()=>{
      setError('')
      void runApi.exportResultsCsv(accountId,selected.id).then(({blob,filename})=>{
        const url=URL.createObjectURL(blob)
        const anchor=document.createElement('a'); anchor.href=url; anchor.download=filename; anchor.click()
        URL.revokeObjectURL(url)
      }).catch(e=>setError(e instanceof Error?e.message:'Kunde inte exportera CSV.'))
    }}>Exportera resultat (CSV)</button><button onClick={()=>{
      setError('')
      void runApi.exportPackage(accountId,selected.id).then(({blob,filename})=>{
        const url=URL.createObjectURL(blob)
        const anchor=document.createElement('a'); anchor.href=url; anchor.download=filename; anchor.click()
        URL.revokeObjectURL(url)
      }).catch(e=>setError(e instanceof Error?e.message:'Kunde inte exportera paketet.'))
    }}>Exportera komplett paket</button><span className="muted">Presentationsläget öppnas med en separat, tidsbegränsad read-only-länk.</span></div>
    <div className="results-section">
      <div className="section-heading"><div><p className="eyebrow">Live-resultat</p><h2>Resultat per fråga</h2><p className="muted">Strukturerade svar uppdateras automatiskt när deltagarna svarar.</p></div></div>
      {results.length === 0 ? <div className="card empty"><p>Inga frågor eller svar att visa ännu.</p></div> : <div className="result-list">{results.map(result => <QuestionResultCard key={result.questionId} result={result}/>)}</div>}
    </div>
    </>}
  </section>
}

function PresentationQuestion({ result }: { result: QuestionResult }) {
  if (result.type === 'TEXT') {
    return <section className="presentation-question presentation-hidden-text">
      <p className="eyebrow">Fritext</p>
      <h2>{result.position + 1}. {result.text}</h2>
      <p>Fritextsvar visas inte automatiskt i presentationsläge.</p>
      <p className="muted">{result.responseCount} svar har registrerats.</p>
    </section>
  }
  let items: Array<{label:string; count:number}> = []
  if (result.type === 'YES_NO') items = [
    { label: 'Ja', count: result.yesCount ?? 0 },
    { label: 'Nej', count: result.noCount ?? 0 },
  ]
  if (result.type === 'SINGLE_CHOICE' || result.type === 'MULTIPLE_CHOICE') items = result.choices.map(x => ({ label: x.label, count: x.count }))
  if (result.type === 'SCALE') items = result.scale.map(x => ({ label: String(x.value), count: x.count }))
  return <section className="presentation-question">
    <p className="eyebrow">{typeLabels[result.type]}</p>
    <h2>{result.position + 1}. {result.text}</h2>
    <p className="presentation-response-count">{result.responseCount} svar</p>
    <div className="presentation-bars">
      {items.map(item => {
        const max = Math.max(1, ...items.map(x => x.count))
        const width = `${Math.round((item.count / max) * 100)}%`
        const percent = result.responseCount > 0 ? Math.round((item.count / result.responseCount) * 100) : 0
        return <div className="presentation-bar-row" key={item.label}>
          <div className="presentation-bar-label"><span>{item.label}</span><strong>{item.count}</strong></div>
          <div className="presentation-bar-track" aria-label={`${item.label}: ${item.count} svar, ${percent} procent`}><div className="presentation-bar-fill" style={{width}}/></div>
          <span className="presentation-percent">{percent}%</span>
        </div>
      })}
    </div>
  </section>
}

function PresentationView({ token }: { token: string }) {
  const [runTitle, setRunTitle] = useState('Enkät')
  const [summary, setSummary] = useState<RunLiveSummary | null>(null)
  const [results, setResults] = useState<QuestionResult[]>([])
  const [selectedId, setSelectedId] = useState<string>('')
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    let fallbackTimer: number | null = null
    async function refresh() {
      try {
        const view = await presentationApi.view(token)
        if (cancelled) return
        setRunTitle(view.title); setSummary(view.summary); setResults(view.results)
        setSelectedId(current => current && view.results.some(r => r.questionId === current) ? current : (view.results[0]?.questionId ?? ''))
        setError('')
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Kunde inte läsa presentationsdata.')
      }
    }
    function stopFallback(){ if(fallbackTimer!=null){ window.clearInterval(fallbackTimer); fallbackTimer=null } }
    function startFallback(){ if(fallbackTimer==null) fallbackTimer=window.setInterval(()=>void refresh(),5000) }
    void refresh()
    const events = new EventSource(presentationApi.eventsUrl(token))
    const onChange = () => void refresh()
    for (const type of ['connected','participant_started','participant_activity','response_updated','participant_submitted']) events.addEventListener(type,onChange)
    events.onopen=()=>stopFallback()
    events.onerror=()=>startFallback()
    return ()=>{ cancelled=true; stopFallback(); events.close() }
  }, [token])

  const selected = results.find(r => r.questionId === selectedId) ?? null
  return <main className="presentation-page">
    <header className="presentation-header">
      <div><p className="eyebrow">Livepresentation</p><h1>{runTitle}</h1></div>
      <div className="presentation-controls">
        <label>Visa fråga<select value={selectedId} onChange={e=>setSelectedId(e.target.value)} disabled={results.length===0}>
          {results.map(r=><option key={r.questionId} value={r.questionId}>{r.position+1}. {r.text}</option>)}
        </select></label>
        <button onClick={()=>window.close()}>Stäng</button>
      </div>
    </header>
    {error && <div className="error" role="alert">{error}</div>}
    <section className="presentation-summary" aria-live="polite">
      <div><strong>{summary?.started ?? '–'}</strong><span>påbörjat</span></div>
      <div><strong>{summary?.active ?? '–'}</strong><span>svarar nu</span></div>
      <div><strong>{summary?.submitted ?? '–'}</strong><span>klara</span></div>
    </section>
    <section className="presentation-stage">
      {selected ? <PresentationQuestion result={selected}/> : <div className="presentation-empty"><h2>Inga frågor att visa ännu</h2><p>Resultatet visas här när genomförandet innehåller frågor.</p></div>}
    </section>
  </main>
}

function SurveyEditor({ accountId, id, onDone }: { accountId:string; id:string|null; onDone:()=>void }) {
  const [model,setModel]=useState<SurveyInput>({title:'',description:'',status:'DRAFT',questions:[]})
  const [loading,setLoading]=useState(Boolean(id)); const [saving,setSaving]=useState(false); const [error,setError]=useState(''); const [saved,setSaved]=useState(false)
  useEffect(()=>{ if(!id)return; setLoading(true); surveyApi.get(accountId,id).then((s:SurveyView)=>setModel({title:s.title,description:s.description??'',status:s.status,questions:s.questions.map(q=>({type:q.type,text:q.text,required:q.required,scaleMin:q.scaleMin,scaleMax:q.scaleMax,scaleMinLabel:q.scaleMinLabel,scaleMaxLabel:q.scaleMaxLabel,options:q.options.map(o=>({value:o.value,label:o.label}))}))})).catch(e=>setError(e instanceof Error?e.message:'Kunde inte läsa enkäten.')).finally(()=>setLoading(false)) },[accountId,id])
  const validation=useMemo(()=>validate(model),[model])
  function updateQuestion(i:number,q:QuestionInput){ setModel({...model,questions:model.questions.map((x,j)=>j===i?q:x)}); setSaved(false) }
  function move(i:number,d:number){ const q=[...model.questions]; const [item]=q.splice(i,1); q.splice(i+d,0,item); setModel({...model,questions:q});setSaved(false) }
  async function save(){ const errors=validate(model); if(errors.length){ setError(errors.join(' ')); return } setSaving(true);setError('');try{ id?await surveyApi.update(accountId,id,model):await surveyApi.create(accountId,model);setSaved(true); if(!id) onDone() }catch(e){setError(e instanceof Error?e.message:'Kunde inte spara enkäten.')}finally{setSaving(false)} }
  async function exportDefinition(){
    if(!id) return
    setError('')
    try {
      const { blob, filename } = await surveyApi.exportDefinition(accountId,id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = filename; anchor.click()
      URL.revokeObjectURL(url)
    } catch(e) { setError(e instanceof Error ? e.message : 'Kunde inte exportera enkäten.') }
  }
  if(loading) return <div className="card">Läser in enkäten…</div>
  return <section className="editor"><div className="page-heading"><div><button className="back" onClick={onDone}>← Enkäter</button><p className="eyebrow">{id?'Redigera enkät':'Ny enkät'}</p><h1>{id ? model.title || 'Namnlös enkät' : 'Skapa enkät'}</h1></div><div className="save-area">{saved&&<span className="saved">Sparad</span>}{id&&<button onClick={()=>document.getElementById('workshop')?.scrollIntoView({behavior:'smooth'})}>Workshop</button>}{id&&<button onClick={()=>void exportDefinition()}>Exportera JSON</button>}<button className="primary" disabled={saving||validation.length>0} onClick={()=>void save()}>{saving?'Sparar…':'Spara'}</button></div></div>
    {error&&<div className="error" role="alert">{error}</div>}
    <div className="card form-card"><label>Titel<input value={model.title} onChange={e=>{setModel({...model,title:e.target.value});setSaved(false)}} placeholder="Exempel: DevOps-workshop"/></label><label>Introduktion<textarea rows={3} value={model.description} onChange={e=>{setModel({...model,description:e.target.value});setSaved(false)}} placeholder="Kort instruktion till deltagarna…"/></label></div>
    <div className="section-heading"><div><h2>Frågor</h2><p className="muted">Dra inte runt – använd pilarna för en tydlig och tillgänglig ordning.</p></div><button onClick={()=>setModel({...model,questions:[...model.questions,emptyQuestion()]})}>+ Lägg till fråga</button></div>
    {model.questions.length===0?<div className="card empty"><h3>Inga frågor ännu</h3><p>Lägg till den första frågan. Du kan sedan välja frågetyp.</p><button className="primary" onClick={()=>setModel({...model,questions:[emptyQuestion()]})}>+ Lägg till fråga</button></div>:<div className="question-list">{model.questions.map((q,i)=><QuestionEditor key={i} q={q} index={i} total={model.questions.length} onChange={x=>updateQuestion(i,x)} onMove={d=>move(i,d)} onRemove={()=>setModel({...model,questions:model.questions.filter((_,j)=>j!==i)})}/>)}</div>}
    {id && <SharePanel accountId={accountId} surveyId={id} surveyTitle={model.title} />}
  </section>
}


function ParticipantEntry({ publicId }: { publicId: string }) {
  const [state, setState] = useState<'loading'|'ready'|'error'>('loading')
  const [message, setMessage] = useState('Förbereder din anonyma deltagarsession…')
  const [survey, setSurvey] = useState<ParticipantSurveyView | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [answers, setAnswers] = useState<Record<string, string | boolean | number | string[]>>({})
  const [saveState, setSaveState] = useState<'saved'|'saving'|'error'>('saved')
  const [submitState, setSubmitState] = useState<'idle'|'submitting'|'submitted'|'error'>('idle')
  const [submitError, setSubmitError] = useState('')
  const timers = useRef<Record<string, number>>({})

  useEffect(() => {
    let cancelled = false
    async function start() {
      const key = `survey-session::${publicId}`
      const existing = localStorage.getItem(key)
      try {
        let session
        try { session = existing ? await participantApi.resume(publicId, existing) : await participantApi.create(publicId) }
        catch (e) { if (!existing) throw e; localStorage.removeItem(key); session = await participantApi.create(publicId) }
        if (cancelled) return
        localStorage.setItem(key, session.participantToken)
        setToken(session.participantToken)
        const [loadedSurvey, saved] = await Promise.all([
          participantApi.survey(publicId, session.participantToken),
          participantApi.responses(publicId, session.participantToken),
        ])
        if (cancelled) return
        const restored: Record<string, string | boolean | number | string[]> = {}
        for (const a of saved.answers) {
          if (a.textValue != null) restored[a.questionId] = a.textValue
          else if (a.booleanValue != null) restored[a.questionId] = a.booleanValue
          else if (a.numericValue != null) restored[a.questionId] = a.numericValue
          else if (a.optionValues.length > 0) {
            const q = loadedSurvey.questions.find(question => question.id === a.questionId)
            restored[a.questionId] = q?.type === 'MULTIPLE_CHOICE' ? a.optionValues : a.optionValues[0]
          }
        }
        setAnswers(restored)
        setSurvey(loadedSurvey)
        setMessage(session.resumed ? 'Din tidigare deltagarsession har återupptagits.' : 'Du deltar anonymt i enkäten.')
        setState('ready')
      } catch (e) {
        if (cancelled) return
        setMessage(e instanceof Error ? e.message : 'Kunde inte starta deltagarsessionen.')
        setState('error')
      }
    }
    void start()
    return () => { cancelled = true; Object.values(timers.current).forEach(window.clearTimeout) }
  }, [publicId])

  useEffect(() => {
    if (state !== 'ready' || !token || submitState === 'submitted') return
    let cancelled = false
    const beat = async () => {
      try { await participantApi.heartbeat(publicId, token) }
      catch { if (!cancelled) { /* heartbeat failure is non-blocking; autosave/submit surface actionable errors */ } }
    }
    void beat()
    const interval = window.setInterval(() => void beat(), 30_000)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [publicId, state, token, submitState])

  function payload(q: ParticipantSurveyView['questions'][number], value: string | boolean | number | string[]) {
    return {
      textValue: q.type === 'TEXT' ? String(value ?? '') : null,
      booleanValue: q.type === 'YES_NO' && typeof value === 'boolean' ? value : null,
      numericValue: q.type === 'SCALE' && typeof value === 'number' ? value : null,
      optionValues: q.type === 'SINGLE_CHOICE' ? (value ? [String(value)] : []) : q.type === 'MULTIPLE_CHOICE' && Array.isArray(value) ? value : [],
    }
  }

  function setAnswer(questionId: string, value: string | boolean | number | string[]) {
    setAnswers(current => ({ ...current, [questionId]: value }))
    if (!survey || !token) return
    const q = survey.questions.find(x => x.id === questionId)
    if (!q) return
    window.clearTimeout(timers.current[questionId])
    setSaveState('saving')
    timers.current[questionId] = window.setTimeout(async () => {
      try { await participantApi.saveResponse(publicId, questionId, token, payload(q, value)); setSaveState('saved') }
      catch { setSaveState('error') }
    }, q.type === 'TEXT' ? 600 : 250)
  }

  function toggleMultiple(questionId: string, optionValue: string) {
    const current = Array.isArray(answers[questionId]) ? answers[questionId] as string[] : []
    const next = current.includes(optionValue) ? current.filter(v => v !== optionValue) : [...current, optionValue]
    setAnswer(questionId, next)
  }

  function isAnswered(q: ParticipantSurveyView['questions'][number]) {
    const value = answers[q.id]
    if (q.type === 'TEXT') return typeof value === 'string' && value.trim().length > 0
    if (q.type === 'YES_NO') return typeof value === 'boolean'
    if (q.type === 'SCALE') return typeof value === 'number'
    if (q.type === 'SINGLE_CHOICE') return typeof value === 'string' && value.length > 0
    if (q.type === 'MULTIPLE_CHOICE') return Array.isArray(value) && value.length > 0
    return false
  }

  async function submitAnswers() {
    if (!survey || !token || submitState === 'submitting') return
    const missing = survey.questions.filter(q => q.required && !isAnswered(q))
    if (missing.length > 0) {
      setSubmitError(`Besvara alla obligatoriska frågor. ${missing.length} ${missing.length === 1 ? 'fråga saknas' : 'frågor saknas'}.`)
      setSubmitState('error')
      document.getElementById(`question-${missing[0].id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
    setSubmitState('submitting'); setSubmitError(''); setSaveState('saving')
    Object.values(timers.current).forEach(window.clearTimeout)
    try {
      for (const q of survey.questions) {
        if (answers[q.id] !== undefined) await participantApi.saveResponse(publicId, q.id, token, payload(q, answers[q.id]))
      }
      setSaveState('saved')
      await participantApi.submit(publicId, token)
      setSubmitState('submitted')
    } catch (e) {
      setSaveState('error')
      setSubmitState('error')
      setSubmitError(e instanceof Error ? e.message : 'Kunde inte skicka in enkäten.')
    }
  }

  if (state !== 'ready' || !survey) {
    return <main className="center-shell participant-shell"><section className="card participant-entry">
      <p className="eyebrow">Enkät</p><h1>{state === 'error' ? 'Kunde inte ansluta' : 'Ansluter…'}</h1>
      <p className={state === 'error' ? 'error' : 'muted'} role={state === 'error' ? 'alert' : undefined} aria-live="polite">{message}</p>
    </section></main>
  }

  if (submitState === 'submitted') {
    return <main className="center-shell participant-shell"><section className="card participant-entry">
      <p className="eyebrow">Enkät inskickad</p><h1>Tack för ditt svar</h1>
      <p className="muted">Dina svar är nu registrerade. Du kan stänga den här sidan.</p>
    </section></main>
  }

  const answeredCount = survey.questions.filter(isAnswered).length
  return <><a className="skip-link" href="#participant-main">Hoppa till enkäten</a><main id="participant-main" className="participant-page" tabIndex={-1}>
    <section className="participant-header">
      <p className="eyebrow">Anonym enkät</p><h1>{survey.title}</h1>
      <div className="participant-meta"><span>{answeredCount} av {survey.questions.length} besvarade</span><span>•</span><span>Inget konto krävs</span></div>
      <div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={survey.questions.length} aria-valuenow={answeredCount} aria-label={`${answeredCount} av ${survey.questions.length} frågor besvarade`}><div className="progress-fill" style={{width: `${survey.questions.length ? Math.round(answeredCount / survey.questions.length * 100) : 0}%`}} /></div>
      <p className={saveState === 'error' ? 'save-status error' : 'save-status'} aria-live="polite">
        {saveState === 'saving' ? 'Sparar…' : saveState === 'error' ? 'Kunde inte spara. Ändra svaret igen för att försöka på nytt.' : 'Svar sparade'}
      </p>
    </section>
    <form className="participant-form" onSubmit={e => e.preventDefault()}>
      {survey.questions.map((q, index) => <fieldset className="participant-question" id={`question-${q.id}`} key={q.id}>
        <legend><span className="question-index">{index + 1}</span><span>{q.text}{q.required && <span className="required-mark" aria-label="Obligatorisk"> *</span>}</span></legend>
        {q.type === 'TEXT' && <textarea aria-label={q.text} rows={4} value={typeof answers[q.id] === 'string' ? answers[q.id] as string : ''} onChange={e => setAnswer(q.id, e.target.value)} placeholder="Skriv ditt svar…" required={q.required}/>} 
        {q.type === 'YES_NO' && <div className="choice-grid two-columns">{[{label:'Ja', value:true},{label:'Nej',value:false}].map(option => <label className="choice-card" key={String(option.value)}><input type="radio" name={q.id} checked={answers[q.id] === option.value} onChange={() => setAnswer(q.id, option.value)} required={q.required}/><span>{option.label}</span></label>)}</div>}
        {q.type === 'SINGLE_CHOICE' && <select aria-label={q.text} value={typeof answers[q.id] === 'string' ? answers[q.id] as string : ''} onChange={e => setAnswer(q.id, e.target.value)} required={q.required}><option value="">Välj ett alternativ…</option>{q.options.map(o => <option key={o.id} value={o.value}>{o.label}</option>)}</select>}
        {q.type === 'MULTIPLE_CHOICE' && <div className="choice-list">{q.options.map(o => { const selected = Array.isArray(answers[q.id]) && (answers[q.id] as string[]).includes(o.value); return <label className="choice-card" key={o.id}><input type="checkbox" checked={selected} onChange={() => toggleMultiple(q.id, o.value)}/><span>{o.label}</span></label> })}</div>}
        {q.type === 'SCALE' && <div><div className="scale-labels"><span>{q.scaleMinLabel || q.scaleMin}</span><span>{q.scaleMaxLabel || q.scaleMax}</span></div><div className="scale-choices">{Array.from({length: Math.max(0, (q.scaleMax ?? 5) - (q.scaleMin ?? 1) + 1)}, (_, i) => (q.scaleMin ?? 1) + i).map(value => <label className="scale-choice" key={value}><input type="radio" name={q.id} checked={answers[q.id] === value} onChange={() => setAnswer(q.id, value)} required={q.required}/><span>{value}</span></label>)}</div></div>}
      </fieldset>)}
      <div className="participant-footer"><div><p className="muted">Svaren sparas automatiskt. Kontrollera dem och skicka sedan in.</p>{submitError && <p className="error" role="alert">{submitError}</p>}</div><button className="primary" type="button" disabled={submitState === 'submitting' || saveState === 'saving'} onClick={() => void submitAnswers()}>{submitState === 'submitting' ? 'Skickar…' : 'Skicka svar'}</button></div>
    </form>
  </main></>
}

export function App() {
  const path = window.location.pathname
  const participantMatch = path.match(/^\/r\/([^/]+)\/?$/)
  if (participantMatch) return <ParticipantEntry publicId={decodeURIComponent(participantMatch[1])} />
  const presentationMatch = path.match(/^\/present\/([^/]+)\/?$/)
  if (presentationMatch) return <PresentationView token={decodeURIComponent(presentationMatch[1])} />
  if (path === '/' || path === '') return <JoinLanding />
  if (path === '/admin/set-password') return <SetPassword token={new URLSearchParams(window.location.search).get('token')} />

  const accountMatch = path.match(/^\/admin\/accounts\/([^/]+)\/?$/)
  const [auth,setAuth]=useState<{loading:boolean;username:string|null;systemAdmin:boolean}>({loading:true,username:null,systemAdmin:false})
  const [accounts,setAccounts]=useState<SurveyAccountMembership[]>([])
  const [accountsLoading,setAccountsLoading]=useState(true)
  const [accountId,setAccountId]=useState<string|null>(accountMatch ? decodeURIComponent(accountMatch[1]) : null)
  const [screen,setScreen]=useState<Screen>({kind:'list'})

  async function loadAccounts(){
    setAccountsLoading(true)
    try {
      const items=await accountApi.list()
      setAccounts(items)
      if (!accountId && items.length===1) {
        selectAccount(items[0].id,true)
      } else if (accountId && !items.some(item=>item.id===accountId)) {
        setAccountId(null)
        window.history.replaceState(null,'','/admin')
      }
    } catch {
      setAccounts([])
    } finally {
      setAccountsLoading(false)
    }
  }

  function selectAccount(id:string,replace=false){
    setAccountId(id)
    setScreen({kind:'list'})
    const target=`/admin/accounts/${encodeURIComponent(id)}`
    if(replace) window.history.replaceState(null,'',target)
    else window.history.pushState(null,'',target)
  }

  function clearAccount(){
    setAccountId(null)
    setScreen({kind:'list'})
    window.history.pushState(null,'','/admin')
  }

  useEffect(()=>{
    authApi.me().then(x=>{
      setAuth({loading:false,username:x.username,systemAdmin:x.systemAdmin})
    }).catch(()=>setAuth({loading:false,username:null,systemAdmin:false}))
  },[])

  useEffect(()=>{
    if(auth.username) void loadAccounts()
    else { setAccounts([]); setAccountsLoading(false) }
  },[auth.username])

  if (!path.startsWith('/admin')) return <JoinLanding />
  if(auth.loading || (auth.username && accountsLoading)) return <main className="center-shell"><div className="card">Startar…</div></main>
  if(!auth.username) return <Login onLoggedIn={username=>{
    setAuth({loading:true,username,systemAdmin:false})
    authApi.me().then(x=>setAuth({loading:false,username:x.username,systemAdmin:x.systemAdmin})).catch(()=>setAuth({loading:false,username:null,systemAdmin:false}))
  }}/>

  if (screen.kind==='password') {
    return <><a className="skip-link" href="#main-content">Hoppa till huvudinnehåll</a>
      <header className="topbar"><div><strong>Survey Service</strong><span>Konto</span></div><div><span className="username">{auth.username}</span><button onClick={()=>void authApi.logout().then(()=>setAuth({loading:false,username:null,systemAdmin:false}))}>Logga ut</button></div></header>
      <main id="main-content" className="app-shell" tabIndex={-1}><ChangePassword onDone={()=>setScreen(screen.returnTo==='system'?{kind:'system'}:{kind:'list'})}/></main></>
  }

  if (!accountId && screen.kind !== 'system') {
    return <AccountChooser accounts={accounts} onChoose={id=>selectAccount(id)} systemAdmin={auth.systemAdmin} onSystem={()=>setScreen({kind:'system'})} onPassword={()=>setScreen({kind:'password',returnTo:'list'})} />
  }

  const currentAccount=accountId ? accounts.find(a=>a.id===accountId) ?? null : null

  if (screen.kind==='system') {
    return <><a className="skip-link" href="#main-content">Hoppa till huvudinnehåll</a>
      <header className="topbar"><div><strong>Survey Service</strong><span>Systemadmin</span></div><div><button onClick={()=>setScreen({kind:'password',returnTo:'system'})}>Ändra lösenord</button><span className="username">{auth.username}</span><button onClick={()=>void authApi.logout().then(()=>setAuth({loading:false,username:null,systemAdmin:false}))}>Logga ut</button></div></header>
      <main id="main-content" className="app-shell" tabIndex={-1}><SystemAccountPanel onDone={()=>accountId?setScreen({kind:'list'}):setScreen({kind:'list'})} onChanged={loadAccounts}/></main></>
  }

  if (!currentAccount) {
    return <AccountChooser accounts={accounts} onChoose={id=>selectAccount(id)} systemAdmin={auth.systemAdmin} onSystem={()=>setScreen({kind:'system'})} onPassword={()=>setScreen({kind:'password',returnTo:'list'})} />
  }

  return <><a className="skip-link" href="#main-content">Hoppa till huvudinnehåll</a>
    <header className="topbar">
      <div><strong>Survey Service</strong><span>Admin</span><span className="account-name">{currentAccount.name}</span></div>
      <div className="topbar-actions">
        {accounts.length>1&&<button onClick={clearAccount}>Byt konto</button>}
        <button onClick={()=>setScreen({kind:'admins'})}>Administratörer</button>
        {auth.systemAdmin&&<button onClick={()=>setScreen({kind:'system'})}>Systemadministration</button>}
        <button onClick={()=>setScreen({kind:'password',returnTo:'list'})}>Ändra lösenord</button>
        <span className="username">{auth.username}</span>
        <button onClick={()=>void authApi.logout().then(()=>setAuth({loading:false,username:null,systemAdmin:false}))}>Logga ut</button>
      </div>
    </header>
    <main id="main-content" className="app-shell" tabIndex={-1}>
      {screen.kind==='list'
        ? <SurveyList accountId={currentAccount.id} onEdit={id=>setScreen({kind:'edit',id})}/>
        : screen.kind==='edit'
          ? <SurveyEditor accountId={currentAccount.id} id={screen.id} onDone={()=>setScreen({kind:'list'})}/>
          : <AccountAdminPanel accountId={currentAccount.id} onDone={()=>setScreen({kind:'list'})}/>}
    </main>
  </>
}
