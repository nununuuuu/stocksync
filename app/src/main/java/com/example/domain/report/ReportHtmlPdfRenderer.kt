package com.example.domain.report

import com.example.data.local.ResearchNoteEntity
import com.example.data.model.StockQuote

data class FullResearchReportData(
    val reportDate: String,
    val title: String,
    val executiveSummary: String,
    val oneSentenceVerdict: String,
    val marketBigPicture: String,
    val keyMetricCards: List<MetricCardData>,
    val chipAnalysisSummary: String,
    val chipCandidateRankings: List<ChipRankingData>,
    val newsRadarVerification: String,
    val technicalSummary: String,
    val threeWayMatrix: List<ThreeWayMatrixRow>,
    val tradePlans: List<TradePlanData>,
    val scenarioA: ScenarioData,
    val scenarioB: ScenarioData,
    val scenarioC: ScenarioData,
    val riskAndCompliance: String,
    val dataSourceCitations: List<String>
)

data class MetricCardData(
    val label: String,
    val value: String,
    val change: String,
    val isPositive: Boolean,
    val subText: String
)

data class ChipRankingData(
    val grade: String, // A, B, C, D, E
    val gradeTitle: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val foreignBuy: Long,
    val trustBuy: Long,
    val marginTrend: String,
    val rationale: String
)

data class ThreeWayMatrixRow(
    val symbol: String,
    val name: String,
    val newsRating: String,
    val chipsRating: String,
    val technicalRating: String,
    val alignment: String, // ★★★ 三同向, ★★ 觀察, ★ 過熱, ☆ 背離
    val actionDecision: String
)

data class TradePlanData(
    val priority: String, // 🥇 第一階：立即執行, 🥈 第二階：條件單, 🚫 不做清單
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val strategyTrack: String, // 長期存股 / 兩週爆發
    val entryZone: String,
    val breakoutConfirmPrice: Double,
    val stopLossPrice: Double,
    val target1Price: Double,
    val target2Price: Double,
    val fibKeyLevel: String,
    val invalidationCondition: String,
    val holdingPeriod: String,
    val riskRewardRatio: String
)

data class ScenarioData(
    val name: String, // Scenario A: 多方延續, Scenario B: 震盪整理, Scenario C: 轉弱/破位
    val triggerCondition: String,
    val marketJudgment: String,
    val candidateAction: String,
    val cancelledStrategies: String
)

object ReportHtmlPdfRenderer {

    fun generateHtmlReport(data: FullResearchReportData): String {
        val metricCardsHtml = data.keyMetricCards.joinToString("") { card ->
            val colorClass = if (card.isPositive) "text-up" else "text-down"
            val badgeBg = if (card.isPositive) "rgba(239, 68, 68, 0.15)" else "rgba(34, 197, 94, 0.15)"
            val badgeBorder = if (card.isPositive) "rgba(239, 68, 68, 0.4)" else "rgba(34, 197, 94, 0.4)"
            """
            <div class="metric-card">
                <div class="metric-label">${card.label}</div>
                <div class="metric-value ${colorClass}">${card.value}</div>
                <div class="metric-badge" style="background: ${badgeBg}; border: 1px solid ${badgeBorder}; color: ${if (card.isPositive) "#ef4444" else "#22c55e"};">
                    ${card.change}
                </div>
                <div class="metric-sub">${card.subText}</div>
            </div>
            """.trimIndent()
        }

        val matrixRowsHtml = data.threeWayMatrix.joinToString("") { row ->
            val badgeColor = when {
                row.alignment.contains("★★★") -> "#a855f7"
                row.alignment.contains("★★") -> "#38bdf8"
                row.alignment.contains("★") -> "#f59e0b"
                else -> "#64748b"
            }
            """
            <tr>
                <td style="font-weight: 700; color: #f8fafc;">${row.symbol} ${row.name}</td>
                <td><span class="pill">${row.newsRating}</span></td>
                <td><span class="pill">${row.chipsRating}</span></td>
                <td><span class="pill">${row.technicalRating}</span></td>
                <td><span class="pill" style="background: ${badgeColor}22; color: ${badgeColor}; border: 1px solid ${badgeColor}; font-weight: 700;">${row.alignment}</span></td>
                <td style="font-weight: 600; color: #38bdf8;">${row.actionDecision}</td>
            </tr>
            """.trimIndent()
        }

        val tradePlansHtml = data.tradePlans.joinToString("") { plan ->
            val isBuy = !plan.priority.contains("不做")
            val cardBorder = if (isBuy) "rgba(56, 189, 248, 0.4)" else "rgba(239, 68, 68, 0.4)"
            """
            <div class="trade-plan-card" style="border-left: 4px solid ${if (isBuy) "#38bdf8" else "#ef4444"};">
                <div class="plan-header">
                    <span class="plan-priority">${plan.priority}</span>
                    <span class="plan-title">${plan.symbol} ${plan.name}（現價 NT$ ${plan.currentPrice}）</span>
                    <span class="plan-track">${plan.strategyTrack}</span>
                </div>
                <div class="plan-grid">
                    <div class="plan-item">
                        <span class="plan-item-label">理想進場區</span>
                        <span class="plan-item-val" style="color: #38bdf8;">${plan.entryZone}</span>
                    </div>
                    <div class="plan-item">
                        <span class="plan-item-label">突破確認價</span>
                        <span class="plan-item-val">NT$ ${plan.breakoutConfirmPrice}</span>
                    </div>
                    <div class="plan-item">
                        <span class="plan-item-label">停損防守位</span>
                        <span class="plan-item-val text-down">NT$ ${plan.stopLossPrice}</span>
                    </div>
                    <div class="plan-item">
                        <span class="plan-item-label">第一 / 第二目標</span>
                        <span class="plan-item-val text-up">NT$ ${plan.target1Price} / ${plan.target2Price}</span>
                    </div>
                    <div class="plan-item">
                        <span class="plan-item-label">斐波關鍵位</span>
                        <span class="plan-item-val" style="color: #f59e0b;">${plan.fibKeyLevel}</span>
                    </div>
                    <div class="plan-item">
                        <span class="plan-item-label">風報比 / 持有期</span>
                        <span class="plan-item-val">${plan.riskRewardRatio} ｜ ${plan.holdingPeriod}</span>
                    </div>
                </div>
                <div class="plan-invalidation">
                    <strong>⚠️ 策略失效與出場條件：</strong> ${plan.invalidationCondition}
                </div>
            </div>
            """.trimIndent()
        }

        val citationsHtml = data.dataSourceCitations.joinToString("<br>") { "• $it" }

        return """
        <!DOCTYPE html>
        <html lang="zh-TW">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${data.title} - 台股 AI 投資研究部門</title>
            <style>
                :root {
                    --bg-dark: #090d16;
                    --surface-dark: #121826;
                    --surface-card: #182234;
                    --border-color: #233148;
                    --primary: #38bdf8;
                    --primary-glow: rgba(56, 189, 248, 0.2);
                    --accent: #a855f7;
                    --up-color: #ef4444;   /* Taiwan Stock Market Red is UP */
                    --down-color: #22c55e; /* Taiwan Stock Market Green is DOWN */
                    --text-main: #f8fafc;
                    --text-sub: #94a3b8;
                    --text-muted: #64748b;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans TC", "PingFang TC", sans-serif;
                    background-color: var(--bg-dark);
                    color: var(--text-main);
                    line-height: 1.6;
                    padding: 16px;
                    max-width: 960px;
                    margin: 0 auto;
                }
                .report-hero {
                    background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 60%, #090d16 100%);
                    border: 1px solid var(--border-color);
                    border-radius: 16px;
                    padding: 24px;
                    margin-bottom: 20px;
                    box-shadow: 0 8px 32px rgba(0,0,0,0.4);
                }
                .hero-meta {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    font-size: 12px;
                    color: var(--text-sub);
                    margin-bottom: 12px;
                }
                .badge-official {
                    background: rgba(56, 189, 248, 0.15);
                    color: var(--primary);
                    border: 1px solid rgba(56, 189, 248, 0.4);
                    padding: 3px 8px;
                    border-radius: 6px;
                    font-weight: 700;
                }
                h1 {
                    font-size: 22px;
                    font-weight: 800;
                    color: #ffffff;
                    margin-bottom: 10px;
                    letter-spacing: -0.5px;
                }
                .verdict-box {
                    background: rgba(168, 85, 247, 0.12);
                    border: 1px solid rgba(168, 85, 247, 0.4);
                    border-left: 4px solid var(--accent);
                    border-radius: 8px;
                    padding: 12px 16px;
                    margin-top: 14px;
                    font-size: 14px;
                    color: #f1f5f9;
                }
                .metric-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
                    gap: 12px;
                    margin-bottom: 20px;
                }
                .metric-card {
                    background: var(--surface-card);
                    border: 1px solid var(--border-color);
                    border-radius: 12px;
                    padding: 12px;
                    text-align: center;
                }
                .metric-label { font-size: 11px; color: var(--text-sub); margin-bottom: 4px; }
                .metric-value { font-size: 18px; font-weight: 800; }
                .metric-badge {
                    display: inline-block;
                    font-size: 10px;
                    font-weight: 700;
                    padding: 2px 6px;
                    border-radius: 4px;
                    margin-top: 4px;
                }
                .metric-sub { font-size: 9px; color: var(--text-muted); margin-top: 4px; }
                .text-up { color: var(--up-color); }
                .text-down { color: var(--down-color); }

                .section-card {
                    background: var(--surface-dark);
                    border: 1px solid var(--border-color);
                    border-radius: 14px;
                    padding: 20px;
                    margin-bottom: 20px;
                }
                .section-header {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    margin-bottom: 14px;
                    padding-bottom: 10px;
                    border-bottom: 1px solid var(--border-color);
                }
                .section-header h2 { font-size: 16px; font-weight: 700; color: var(--primary); }
                .section-content { font-size: 13.5px; color: #cbd5e1; white-space: pre-line; line-height: 1.7; }

                /* Table Styling */
                .table-responsive { width: 100%; overflow-x: auto; margin-top: 10px; }
                table { width: 100%; border-collapse: collapse; text-align: left; font-size: 12.5px; }
                th { background: #1a2333; color: var(--text-sub); padding: 10px 12px; font-weight: 600; border-bottom: 2px solid var(--border-color); }
                td { padding: 10px 12px; border-bottom: 1px solid var(--border-color); }
                .pill { font-size: 11px; padding: 2px 6px; border-radius: 4px; background: rgba(255,255,255,0.06); }

                /* Trade Plans */
                .trade-plan-card {
                    background: var(--surface-card);
                    border: 1px solid var(--border-color);
                    border-radius: 10px;
                    padding: 14px;
                    margin-bottom: 12px;
                }
                .plan-header { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 10px; }
                .plan-priority { background: rgba(56, 189, 248, 0.2); color: var(--primary); font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 4px; }
                .plan-title { font-size: 15px; font-weight: 800; color: #ffffff; }
                .plan-track { font-size: 11px; color: var(--accent); margin-left: auto; }
                .plan-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
                    gap: 8px;
                    background: rgba(0,0,0,0.25);
                    padding: 10px;
                    border-radius: 8px;
                    margin-bottom: 8px;
                }
                .plan-item { display: flex; flex-direction: column; }
                .plan-item-label { font-size: 10px; color: var(--text-muted); }
                .plan-item-val { font-size: 13px; font-weight: 700; }
                .plan-invalidation { font-size: 11.5px; color: #fca5a5; background: rgba(239, 68, 68, 0.08); padding: 8px 10px; border-radius: 6px; border: 1px solid rgba(239, 68, 68, 0.2); }

                /* Scenarios */
                .scenario-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-top: 10px; }
                .scenario-card { background: var(--surface-card); border-radius: 10px; padding: 14px; border: 1px solid var(--border-color); }
                .sc-title { font-size: 14px; font-weight: 700; margin-bottom: 6px; }
                .sc-cond { font-size: 11.5px; color: var(--text-sub); margin-bottom: 6px; }
                .sc-act { font-size: 12px; color: #e2e8f0; }

                /* Citations Footer */
                .footer-box {
                    background: rgba(0,0,0,0.4);
                    border: 1px dashed var(--border-color);
                    border-radius: 10px;
                    padding: 14px;
                    font-size: 11px;
                    color: var(--text-muted);
                    line-height: 1.8;
                }

                @media print {
                    body { background: #ffffff; color: #000000; }
                    .report-hero, .section-card, .trade-plan-card, .metric-card { background: #ffffff; border: 1px solid #cccccc; color: #000000; }
                    .text-up { color: #cc0000; }
                    .text-down { color: #008800; }
                }
            </style>
        </head>
        <body>
            <div class="report-hero">
                <div class="hero-meta">
                    <span class="badge-official">🏛️ TWSE / TPEx 官方資料源</span>
                    <span>報告日期：${data.reportDate}</span>
                </div>
                <h1>${data.title}</h1>
                <div style="font-size: 13px; color: var(--text-sub);">${data.executiveSummary}</div>
                <div class="verdict-box">
                    <strong>💡 今日一句話決策：</strong> ${data.oneSentenceVerdict}
                </div>
            </div>

            <!-- Key Metric Cards -->
            <div class="metric-grid">
                $metricCardsHtml
            </div>

            <!-- 1. Market Big Picture -->
            <div class="section-card">
                <div class="section-header">
                    <h2>🌐 一、市場全貌與盤勢脈絡 (盤後研究員)</h2>
                </div>
                <div class="section-content">${data.marketBigPicture}</div>
            </div>

            <!-- 2. Institutional Chips & Candidates -->
            <div class="section-card">
                <div class="section-header">
                    <h2>📊 二、三大法人籌碼與分級選股 (籌碼選股分析師)</h2>
                </div>
                <div class="section-content">${data.chipAnalysisSummary}</div>
            </div>

            <!-- 3. News Verification -->
            <div class="section-card">
                <div class="section-header">
                    <h2>📡 三、7日消息面雷達與事件催化驗證 (消息面雷達)</h2>
                </div>
                <div class="section-content">${data.newsRadarVerification}</div>
            </div>

            <!-- 4. Technical & Fibonacci Analysis -->
            <div class="section-card">
                <div class="section-header">
                    <h2>📈 四、技術分析、均線與斐波那契錨點 (策略分析師)</h2>
                </div>
                <div class="section-content">${data.technicalSummary}</div>
            </div>

            <!-- 5. 3-Way Cross Verification Matrix -->
            <div class="section-card">
                <div class="section-header">
                    <h2>🎯 五、三面向同向度交叉驗證矩陣</h2>
                </div>
                <div class="table-responsive">
                    <table>
                        <thead>
                            <tr>
                                <th>個股標的</th>
                                <th>消息面</th>
                                <th>籌碼面</th>
                                <th>技術面</th>
                                <th>同向度</th>
                                <th>決策結論</th>
                            </tr>
                        </thead>
                        <tbody>
                            $matrixRowsHtml
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 6. Complete Trade Plans -->
            <div class="section-card">
                <div class="section-header">
                    <h2>🏆 六、最終候選個股交易計畫 (雙軌操作手冊)</h2>
                </div>
                <div>
                    $tradePlansHtml
                </div>
            </div>

            <!-- 7. Tomorrow's Scenarios -->
            <div class="section-card">
                <div class="section-header">
                    <h2>🔮 七、明日台股三情境劇本 (Scenario Planning)</h2>
                </div>
                <div class="scenario-grid">
                    <div class="scenario-card" style="border-top: 3px solid #ef4444;">
                        <div class="sc-title" style="color: #ef4444;">${data.scenarioA.name}</div>
                        <div class="sc-cond"><strong>觸發條件：</strong>${data.scenarioA.triggerCondition}</div>
                        <div class="sc-act"><strong>應對動作：</strong>${data.scenarioA.candidateAction}</div>
                    </div>
                    <div class="scenario-card" style="border-top: 3px solid #38bdf8;">
                        <div class="sc-title" style="color: #38bdf8;">${data.scenarioB.name}</div>
                        <div class="sc-cond"><strong>觸發條件：</strong>${data.scenarioB.triggerCondition}</div>
                        <div class="sc-act"><strong>應對動作：</strong>${data.scenarioB.candidateAction}</div>
                    </div>
                    <div class="scenario-card" style="border-top: 3px solid #22c55e;">
                        <div class="sc-title" style="color: #22c55e;">${data.scenarioC.name}</div>
                        <div class="sc-cond"><strong>觸發條件：</strong>${data.scenarioC.triggerCondition}</div>
                        <div class="sc-act"><strong>應對動作：</strong>${data.scenarioC.candidateAction}</div>
                    </div>
                </div>
            </div>

            <!-- 8. Citations & Compliance -->
            <div class="footer-box">
                <strong>🏛️ 官方資料來源與合規聲明：</strong><br>
                $citationsHtml
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
