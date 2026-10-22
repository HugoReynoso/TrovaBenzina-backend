import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdtemp, writeFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';

const directory = await mkdtemp(join(tmpdir(), 'mimit-powershell-test-'));
const prices = join(directory, 'prices.csv');
const stations = join(directory, 'stations.csv');
const password = 'test-"quoted"-password';
const token = 'local-test-token-hidden';
const requests = [];
let scenario = '';
const server = createServer(async (request, response) => {
  try {
    let body = '';
    for await (const chunk of request) body += chunk;
    requests.push(request.url);
    response.setHeader('Content-Type', 'application/json');
    if (request.url === '/api/auth/login') {
      assert.deepEqual(JSON.parse(body), { email: 'local-test@example.test', password });
      response.end(JSON.stringify({ token }));
      return;
    }
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    assert.match(request.headers['content-type'], /^multipart\/form-data;/);
    assert.match(body, /name="?prices"?/);
    if (scenario === 'full') assert.match(body, /name="?stations"?/);
    else assert.doesNotMatch(body, /name="?stations"?/);
    if (scenario === 'upload-failure') {
      response.writeHead(400);
      response.end(JSON.stringify({ message: 'Invalid MIMIT CSV' }));
    } else {
      response.end(JSON.stringify({ pricesRead: 1, pricesInserted: 0, pricesUpdated: 1, errors: 0 }));
    }
  } catch (error) {
    response.writeHead(500);
    response.end(JSON.stringify({ message: error.message }));
  }
});

try {
  await writeFile(stations, 'idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine\n123|G|Q8|Stradale|Q8|Via Roma|Milano|MI|45.46|9.19\n');
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  for (scenario of ['full', 'prices', 'bad-csv', 'upload-failure']) {
    requests.length = 0;
    await writeFile(prices, scenario === 'bad-csv' ? '<html>Unavailable</html>'
      : 'Estrazione del 2026-10-22\r\nidImpianto|descCarburante|prezzo|isSelf|dtComu\r\n123|Benzina|1.789|1|22/10/2026 08:00:00\r\n');
    const command = [
      "$ErrorActionPreference = 'Stop'",
      '$secure = ConvertTo-SecureString $env:MIMIT_TEST_PASSWORD -AsPlainText -Force',
      "$credential = [System.Management.Automation.PSCredential]::new('local-test@example.test', $secure)",
      '& $env:MIMIT_TEST_SCRIPT -ApiBaseUrl $env:MIMIT_TEST_API -Credential $credential -Mode $env:MIMIT_TEST_MODE -PricesFile $env:MIMIT_TEST_PRICES -StationsFile $env:MIMIT_TEST_STATIONS'
    ].join('; ');
    const result = await new Promise((resolve, reject) => {
      const child = spawn(process.env.PWSH_TEST_EXE || 'powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], {
        windowsHide: true,
        timeout: 60000,
        env: { ...process.env, TEMP: directory, TMP: directory,
          PSModulePath: join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'Modules'),
          MIMIT_TEST_PASSWORD: password,
          MIMIT_TEST_SCRIPT: resolvePath(),
          MIMIT_TEST_API: `http://127.0.0.1:${server.address().port}`,
          MIMIT_TEST_MODE: scenario === 'full' ? 'full' : 'prices',
          MIMIT_TEST_PRICES: prices, MIMIT_TEST_STATIONS: stations }
      });
      let output = '';
      child.stdout.on('data', data => { output += data; });
      child.stderr.on('data', data => { output += data; });
      child.on('error', reject);
      child.on('close', code => resolve({ code, output }));
    });
    assert.equal(result.code === 0, scenario === 'full' || scenario === 'prices', result.output);
    assert.ok(!result.output.includes(password), 'Password leaked into output');
    assert.ok(!result.output.includes(token), 'JWT leaked into output');
    if (scenario === 'bad-csv') assert.deepEqual(requests, []);
    else assert.deepEqual(requests, ['/api/auth/login', scenario === 'full'
      ? '/api/admin/mimit/import/upload' : '/api/admin/mimit/import/prices/upload']);
    const remaining = await readdir(directory);
    assert.ok(remaining.includes('prices.csv') && remaining.includes('stations.csv'));
    assert.ok(!remaining.some(name => name.startsWith('mimit-local-')), 'Temporary directory was not removed');
    console.log(`PASS: ${scenario}`);
  }
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
  assert.ok(resolve(directory).startsWith(resolve(tmpdir()) + sep + 'mimit-powershell-test-'));
  await rm(directory, { recursive: true, force: true });
}

function resolvePath() {
  return resolve('scripts/import-mimit-local.ps1');
}
