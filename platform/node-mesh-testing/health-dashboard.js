/**
 * Commerce OS — Node Mesh Health Dashboard & Reporter
 * ----------------------------------------------------
 * Generates an executive terminal scoreboard and a standalone HTML visualization
 * mapping out the health of each node and highlighting loose joints.
 */

const fs = require('fs');
const path = require('path');

class HealthDashboard {
  constructor(nodeHealth, pairwiseStats, fuzzerResults, invariantResults) {
    this.nodeHealth = nodeHealth; // Map of NodeId -> { status, score, latency, errors }
    this.pairwiseStats = pairwiseStats;
    this.fuzzerResults = fuzzerResults;
    this.invariantResults = invariantResults;
    this.timestamp = new Date().toISOString();
  }

  printTerminalReport() {
    console.log('\n========================================================================');
    console.log('🏛️  COMMERCE OS — DEEP NODE HEALTH & RESILIENCE SCOREBOARD');
    console.log('========================================================================');
    console.log(`Timestamp: ${this.timestamp}`);
    console.log(`Combinatorial Search Space: ${this.pairwiseStats.totalStateSpace} states`);
    console.log(`Pairwise Reduction: ${this.pairwiseStats.totalPairwisePermutations} pairs covered in ${this.pairwiseStats.testCases.length} tests (${this.pairwiseStats.coveragePercentage}%)`);
    console.log('------------------------------------------------------------------------\n');

    console.log('NODE TOPOLOGY STATUS:');
    console.log('------------------------------------------------------------------------');
    console.log(' NODE ID       | NAME                           | STATUS   | INTEGRITY');
    console.log('------------------------------------------------------------------------');

    for (const [id, data] of Object.entries(this.nodeHealth)) {
      const statusIcon = data.score >= 95 ? '🟢 HEALTHY ' : data.score >= 80 ? '🟡 LOOSE   ' : '🔴 BROKEN  ';
      const paddedId = id.padEnd(13, ' ');
      const paddedName = (data.name || id).padEnd(30, ' ').substring(0, 30);
      const integrity = `${data.score}% (${data.passed}/${data.total})`;
      console.log(` ${paddedId} | ${paddedName} | ${statusIcon} | ${integrity}`);
    }
    console.log('------------------------------------------------------------------------\n');

    console.log('🦍 GORILLA & CHAOS FUZZING HIGHLIGHTS:');
    console.log('------------------------------------------------------------------------');
    const passedFuzz = this.fuzzerResults.filter(r => r.passed).length;
    const totalFuzz = this.fuzzerResults.length;
    console.log(`Resilience Score: ${((passedFuzz / totalFuzz) * 100).toFixed(1)}% (${passedFuzz}/${totalFuzz} adversarial attacks absorbed)`);

    for (const res of this.fuzzerResults) {
      const icon = res.passed ? '✅' : '🚨 [LOOSE JOINT]';
      console.log(`  ${icon} [${res.node}] ${res.test}: ${res.details}`);
    }
    console.log('------------------------------------------------------------------------\n');

    console.log('🛡️ UNIVERSAL INVARIANTS:');
    console.log('------------------------------------------------------------------------');
    for (const inv of this.invariantResults) {
      const icon = inv.passed ? '✅' : '❌ VIOLATION';
      console.log(`  ${icon} ${inv.invariant}: ${inv.message || 'Preserved strictly'}`);
    }
    console.log('========================================================================\n');
  }

  saveJsonReport(destPath) {
    const report = {
      timestamp: this.timestamp,
      nodeHealth: this.nodeHealth,
      pairwiseStats: this.pairwiseStats,
      fuzzerResults: this.fuzzerResults,
      invariantResults: this.invariantResults
    };
    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    fs.writeFileSync(destPath, JSON.stringify(report, null, 2));
    console.log(`💾 JSON Health Report saved to: ${destPath}`);
  }

  saveHtmlReport(destPath) {
    const nodesJson = JSON.stringify(this.nodeHealth);
    const fuzzerJson = JSON.stringify(this.fuzzerResults);
    const pairwiseJson = JSON.stringify(this.pairwiseStats);

    const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Commerce OS — Node Mesh Health Dashboard</title>
  <style>
    :root {
      --bg: #09090b;
      --card-bg: #18181b;
      --border: #27272a;
      --text: #f4f4f5;
      --text-muted: #a1a1aa;
      --accent: #e11d48;
      --green: #10b981;
      --yellow: #f59e0b;
      --red: #ef4444;
      --sky: #0ea5e9;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: var(--bg); color: var(--text); padding: 24px; }
    .header { margin-bottom: 24px; border-bottom: 1px solid var(--border); padding-bottom: 16px; display: flex; justify-content: space-between; align-items: center; }
    .header h1 { font-size: 24px; font-weight: 700; color: #fff; }
    .badge { padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 600; text-transform: uppercase; }
    .badge-healthy { background: rgba(16, 185, 129, 0.2); color: var(--green); border: 1px solid var(--green); }
    .badge-loose { background: rgba(245, 158, 11, 0.2); color: var(--yellow); border: 1px solid var(--yellow); }
    .badge-broken { background: rgba(239, 68, 68, 0.2); color: var(--red); border: 1px solid var(--red); }
    
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .stat-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 18px; }
    .stat-card .label { font-size: 13px; color: var(--text-muted); margin-bottom: 6px; }
    .stat-card .value { font-size: 26px; font-weight: 700; color: #fff; }

    .topology-container { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; margin-bottom: 24px; }
    .node-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin-top: 14px; }
    .node-box { background: #27272a; border-radius: 8px; padding: 14px; border-left: 4px solid var(--green); }
    .node-box.loose { border-left-color: var(--yellow); }
    .node-box.broken { border-left-color: var(--red); }
    .node-box h3 { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
    .node-box .meta { font-size: 12px; color: var(--text-muted); }

    table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
    th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--border); }
    th { color: var(--text-muted); font-weight: 600; }
  </style>
</head>
<body>
  <div class="header">
    <div>
      <h1>🏛️ Commerce OS — Node Mesh Health Dashboard</h1>
      <p style="color: var(--text-muted); font-size: 13px; margin-top: 4px;">Automated Semantic Health, Pairwise Combinatorics & Gorilla Chaos Fuzzing</p>
    </div>
    <span class="badge badge-healthy">Mesh Active</span>
  </div>

  <div class="grid">
    <div class="stat-card">
      <div class="label">Combinatorial State Space</div>
      <div class="value">${Number(this.pairwiseStats.totalStateSpace).toLocaleString()}</div>
      <p style="color: var(--text-muted); font-size: 12px; margin-top: 4px;">Reduced to ${this.pairwiseStats.testCases.length} optimal test vectors</p>
    </div>
    <div class="stat-card">
      <div class="label">Pairwise Coverage</div>
      <div class="value" style="color: var(--sky);">${this.pairwiseStats.coveragePercentage}%</div>
      <p style="color: var(--text-muted); font-size: 12px; margin-top: 4px;">100% 2-way orthogonal interaction proof</p>
    </div>
    <div class="stat-card">
      <div class="label">Gorilla Chaos Resilience</div>
      <div class="value" style="color: var(--green);">${((this.fuzzerResults.filter(r => r.passed).length / this.fuzzerResults.length) * 100).toFixed(1)}%</div>
      <p style="color: var(--text-muted); font-size: 12px; margin-top: 4px;">${this.fuzzerResults.filter(r => r.passed).length} / ${this.fuzzerResults.length} edge attacks absorbed</p>
    </div>
  </div>

  <div class="topology-container">
    <h2 style="font-size: 16px; margin-bottom: 8px;">🌐 Commerce Nodes Health Topology</h2>
    <div class="node-grid">
      ${Object.entries(this.nodeHealth).map(([id, data]) => `
        <div class="node-box ${data.score < 80 ? 'broken' : data.score < 95 ? 'loose' : ''}">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <span style="font-size: 11px; font-weight: 700; color: var(--text-muted);">${id}</span>
            <span class="badge ${data.score < 80 ? 'badge-broken' : data.score < 95 ? 'badge-loose' : 'badge-healthy'}">${data.score}%</span>
          </div>
          <h3>${data.name || id}</h3>
          <div class="meta">${data.passed}/${data.total} checks verified</div>
        </div>
      `).join('')}
    </div>
  </div>

  <div class="topology-container">
    <h2 style="font-size: 16px; margin-bottom: 8px;">🦍 Gorilla Fuzzing & Loose Joint Diagnostic</h2>
    <table>
      <thead>
        <tr>
          <th>Target Node</th>
          <th>Test Name</th>
          <th>Status</th>
          <th>Diagnostic Feedback</th>
        </tr>
      </thead>
      <tbody>
        ${this.fuzzerResults.map(r => `
          <tr>
            <td><strong>${r.node}</strong></td>
            <td>${r.test}</td>
            <td><span class="badge ${r.passed ? 'badge-healthy' : 'badge-loose'}">${r.passed ? 'Passed' : 'Loose Joint'}</span></td>
            <td>${r.details}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  </div>
</body>
</html>`;

    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    fs.writeFileSync(destPath, htmlContent);
    console.log(`🌐 Interactive HTML Health Dashboard saved to: ${destPath}`);
  }
}

module.exports = {
  HealthDashboard
};
