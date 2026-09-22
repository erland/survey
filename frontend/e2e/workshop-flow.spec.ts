import { expect, test, type BrowserContext, type Page } from '@playwright/test'

const surveyTitle = `E2E workshop ${Date.now()}`

async function login(page: Page) {
  await page.goto('/admin')
  await page.getByLabel('Användarnamn').fill('admin')
  await page.getByLabel('Lösenord').fill('change-me')
  await page.getByRole('button', { name: 'Logga in' }).click()
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()
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


test('admin with multiple survey accounts chooses and switches account', async ({ page }) => {
  await login(page)

  const accountName = `E2E account ${Date.now()}`
  await page.getByRole('button', { name: 'Systemadministration' }).click()
  await expect(page.getByRole('heading', { name: 'Enkätkonton' })).toBeVisible()

  await page.getByLabel('Kontonamn').fill(accountName)
  await page.getByLabel('Första administratör').fill('admin')
  await page.getByRole('button', { name: 'Skapa konto' }).click()
  await expect(page.getByText(accountName, { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '← Tillbaka' }).click()
  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()

  await page.getByRole('button', { name: 'Byt konto' }).click()
  await expect(page.getByRole('heading', { name: 'Välj enkätkonto' })).toBeVisible()

  const accountButton = page.getByRole('button').filter({ hasText: accountName })
  await expect(accountButton).toBeVisible()
  await accountButton.click()

  await expect(page.getByRole('heading', { name: 'Mina enkäter' })).toBeVisible()
  await expect(page.locator('.account-name')).toHaveText(accountName)
  await expect(page).toHaveURL(/\/admin\/accounts\/[0-9a-f-]+$/)
})
