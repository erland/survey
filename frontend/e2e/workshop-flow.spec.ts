import { expect, test, type BrowserContext, type Page } from '@playwright/test'

const surveyTitle = `E2E workshop ${Date.now()}`

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/admin')
  await page.getByLabel('E-post eller systemadmin').fill(username)
  await page.getByLabel('Lösenord').fill(password)
  await page.getByRole('button', { name: 'Logga in' }).click()
}

async function login(page: Page) {
  await signIn(page, 'admin', 'change-me')
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()
}

async function setPasswordFromVisibleInvite(page: Page, password: string) {
  const inviteUrl = await page.getByLabel('Länk').inputValue()
  const setup = await page.context().newPage()
  await setup.goto(inviteUrl)
  await setup.getByLabel('Nytt lösenord').fill(password)
  await setup.getByLabel('Upprepa lösenord').fill(password)
  await setup.getByRole('button', { name: 'Sätt lösenord' }).click()
  await expect(setup.getByRole('heading', { name: 'Lösenordet är sparat' })).toBeVisible()
  await setup.close()
}

async function logout(page: Page) {
  await page.getByRole('button', { name: 'Logga ut' }).click()
  await expect(page.getByRole('button', { name: 'Logga in' })).toBeVisible()
}

async function createSimpleSurvey(page: Page, title: string) {
  await page.getByRole('button', { name: '+ Ny enkät' }).click()
  await page.getByLabel('Titel').fill(title)
  await page.getByRole('button', { name: 'Spara' }).click()
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()
  await expect(page.locator('.survey-card').filter({ hasText: title })).toBeVisible()
}

async function createSurvey(page: Page) {
  await page.getByRole('button', { name: '+ Ny enkät' }).click()
  await page.getByLabel('Titel').fill(surveyTitle)
  await page.getByLabel('Introduktion').fill('E2E-test för workshopflödet.')

  await page.getByRole('button', { name: '+ Lägg till fråga' }).first().click()
  const first = page.locator('.question-card').nth(0)
  await first.getByLabel('Frågetyp').selectOption('YES_NO')
  await first.locator('textarea').fill('Är målet tydligt?')
  await first.getByRole('checkbox', { name: 'Obligatorisk fråga' }).check()

  await page.getByRole('button', { name: '+ Lägg till fråga' }).click()
  const second = page.locator('.question-card').nth(1)
  await second.getByLabel('Frågetyp').selectOption('TEXT')
  await second.locator('textarea').fill('Vad vill du förbättra?')

  await page.getByRole('button', { name: 'Spara' }).click()
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()

  const card = page.locator('.survey-card').filter({ hasText: surveyTitle })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: 'Öppna' }).click()
  await expect(page.getByRole('heading', { name: surveyTitle })).toBeVisible()
}

async function startRun(page: Page) {
  await page.getByRole('button', { name: 'Workshop' }).click()
  await page.getByRole('button', { name: 'Starta nytt genomförande' }).click()
  const directLink = page.locator('.share-details input[readonly]')
  await expect(directLink).toBeVisible()
  return directLink.inputValue()
}

async function answerSurvey(context: BrowserContext, directUrl: string) {
  const participant = await context.newPage()
  await participant.goto(directUrl)
  await expect(participant.getByRole('heading', { name: surveyTitle })).toBeVisible()
  await participant.getByRole('radio', { name: 'Ja' }).check()
  await participant.getByLabel('Vad vill du förbättra?').fill('Tydligare nästa steg och ansvar.')
  await expect(participant.getByText('2 av 2 besvarade')).toBeVisible()
  await participant.getByRole('button', { name: 'Skicka svar' }).click()
  await expect(participant.getByRole('heading', { name: 'Tack för ditt svar' })).toBeVisible()
  await participant.close()
}

test('admin creates survey, participant answers, live result, presentation and exports work', async ({ page, browser }) => {
  await login(page)
  await createSurvey(page)

  const definitionDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Exportera JSON' }).click()
  const definition = await definitionDownload
  const definitionPath = await definition.path()
  expect(definitionPath).toBeTruthy()

  const directUrl = await startRun(page)

  const participantContext = await browser.newContext()
  await answerSurvey(participantContext, directUrl)
  await participantContext.close()

  const submittedStat = page.locator('.live-stat').filter({ hasText: 'klara' })
  await expect(submittedStat.locator('strong')).toHaveText('1', { timeout: 15_000 })

  const yesNoResult = page.locator('.result-card').filter({ hasText: 'Är målet tydligt?' })
  await expect(yesNoResult).toContainText('1 svar')
  await expect(yesNoResult).toContainText('Ja')
  await expect(yesNoResult).toContainText('1')

  const textResult = page.locator('.result-card').filter({ hasText: 'Vad vill du förbättra?' })
  await expect(textResult).toContainText('Tydligare nästa steg och ansvar.')

  const popupPromise = page.waitForEvent('popup')
  await page.getByRole('button', { name: 'Öppna presentationsläge' }).click()
  const presentation = await popupPromise
  await expect(presentation.getByRole('heading', { name: surveyTitle })).toBeVisible()
  await expect(presentation.locator('.presentation-summary').getByText('klara')).toBeVisible()
  await expect(presentation.locator('.presentation-summary').locator('strong').last()).toHaveText('1')
  await presentation.getByLabel('Visa fråga').selectOption({ label: '2. Vad vill du förbättra?' })
  await expect(presentation.getByText('Fritextsvar visas inte automatiskt i presentationsläge.')).toBeVisible()
  await presentation.close()

  for (const buttonName of ['Exportera resultat (JSON)', 'Exportera resultat (CSV)', 'Exportera komplett paket']) {
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: buttonName }).click()
    const download = await downloadPromise
    expect(await download.path()).toBeTruthy()
  }

  await page.getByRole('button', { name: '← Enkäter' }).click()
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles(definitionPath!)
  await expect(page.getByRole('heading', { name: surveyTitle })).toBeVisible()
  await expect(page.locator('.question-card')).toHaveCount(2)
})


test('multi-user accounts stay isolated and administrators can manage membership safely', async ({ page }) => {
  const suffix = Date.now()
  const accountA = `E2E account A ${suffix}`
  const accountB = `E2E account B ${suffix}`
  const adminA = `e2e-admin-a-${suffix}@example.test`
  const adminB = `e2e-admin-b-${suffix}@example.test`
  const sharedAdmin = `e2e-shared-${suffix}@example.test`
  const passwordA = 'e2e-password-a-123'
  const passwordB = 'e2e-password-b-123'
  const sharedPassword = 'e2e-shared-password-123'
  const surveyA = `Tenant A survey ${suffix}`
  const surveyB = `Tenant B survey ${suffix}`

  await login(page)
  await page.getByRole('button', { name: 'Systemadministration' }).click()
  await expect(page.getByRole('heading', { name: 'Enkätkonton', exact: true })).toBeVisible()

  for (const [accountName, username, password] of [
    [accountA, adminA, passwordA],
    [accountB, adminB, passwordB],
  ] as const) {
    await page.getByLabel('Kontonamn').fill(accountName)
    await page.getByLabel('Första administratörens e-post').fill(username)
    await page.getByRole('button', { name: 'Skapa konto' }).click()
    await expect(page.getByText(accountName, { exact: true })).toBeVisible()
    await setPasswordFromVisibleInvite(page, password)
  }

  await logout(page)

  await signIn(page, adminA, passwordA)
  await expect(page.locator('.account-name')).toHaveText(accountA)
  await createSimpleSurvey(page, surveyA)

  await page.getByRole('button', { name: 'Administratörer' }).click()
  await page.getByLabel('E-postadress').fill(sharedAdmin)
  await page.getByRole('button', { name: 'Lägg till' }).click()
  await expect(page.locator('.admin-row').filter({ hasText: sharedAdmin })).toBeVisible()
  await setPasswordFromVisibleInvite(page, sharedPassword)

  const sharedRow = page.locator('.admin-row').filter({ hasText: sharedAdmin })
  page.once('dialog', dialog => void dialog.accept())
  await sharedRow.getByRole('button', { name: 'Ta bort' }).click()
  await expect(sharedRow).toHaveCount(0)

  const adminARow = page.locator('.admin-row').filter({ hasText: adminA })
  page.once('dialog', dialog => void dialog.accept())
  await adminARow.getByRole('button', { name: 'Ta bort' }).click()
  await expect(page.getByRole('alert')).toContainText('sista administratören')

  await page.getByLabel('E-postadress').fill(sharedAdmin)
  await page.getByRole('button', { name: 'Lägg till' }).click()
  await expect(page.locator('.admin-row').filter({ hasText: sharedAdmin })).toBeVisible()
  await logout(page)

  await signIn(page, adminB, passwordB)
  await expect(page.locator('.account-name')).toHaveText(accountB)
  await createSimpleSurvey(page, surveyB)
  await page.getByRole('button', { name: 'Administratörer' }).click()
  await page.getByLabel('E-postadress').fill(sharedAdmin)
  await page.getByRole('button', { name: 'Lägg till' }).click()
  await expect(page.locator('.admin-row').filter({ hasText: sharedAdmin })).toBeVisible()
  await logout(page)

  await signIn(page, sharedAdmin, sharedPassword)
  await expect(page.getByRole('heading', { name: 'Välj enkätkonto' })).toBeVisible()
  await expect(page.getByRole('button').filter({ hasText: accountA })).toBeVisible()
  await expect(page.getByRole('button').filter({ hasText: accountB })).toBeVisible()

  await page.getByRole('button').filter({ hasText: accountA }).click()
  await expect(page.locator('.account-name')).toHaveText(accountA)
  await expect(page.locator('.survey-card').filter({ hasText: surveyA })).toBeVisible()
  await expect(page.locator('.survey-card').filter({ hasText: surveyB })).toHaveCount(0)

  await page.getByRole('button', { name: 'Byt konto' }).click()
  await page.getByRole('button').filter({ hasText: accountB }).click()
  await expect(page.locator('.account-name')).toHaveText(accountB)
  await expect(page.locator('.survey-card').filter({ hasText: surveyB })).toBeVisible()
  await expect(page.locator('.survey-card').filter({ hasText: surveyA })).toHaveCount(0)

  await logout(page)
  await login(page)
  await page.getByRole('button', { name: 'Systemadministration' }).click()
  const sharedSystemRow = page.locator('.admin-row').filter({ hasText: sharedAdmin })
  await sharedSystemRow.getByRole('button', { name: 'Skapa återställningslänk' }).click()
  await expect(page.getByRole('heading', { name: `Återställ lösenord för ${sharedAdmin}` })).toBeVisible()
  const resetPassword = 'e2e-reset-password-123'
  await setPasswordFromVisibleInvite(page, resetPassword)

  await logout(page)
  await signIn(page, sharedAdmin, resetPassword)
  await expect(page.getByRole('heading', { name: 'Välj enkätkonto' })).toBeVisible()
})
