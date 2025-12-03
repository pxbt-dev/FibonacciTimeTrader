/**
 * Fibonacci Time Trader - Enhanced Frontend Application
 * SINGLE PAGE - No view tabs
 */

class TimeGeometryDashboard {
    constructor() {
        this.currentSymbol = 'BTC';
        this.charts = new Map();

        this.initialize();
    }

    initialize() {
        console.log('🚀 Initializing Time Geometry Dashboard...');

        // Set up event listeners
        this.setupEventListeners();

        // Load initial data
        this.loadTimeGeometryAnalysis(this.currentSymbol);
        this.loadBacktestResults(this.currentSymbol);
    }

    setupEventListeners() {
        // Symbol tabs
        document.querySelectorAll('.symbol-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                const symbol = tab.dataset.symbol;
                this.switchSymbol(symbol);
            });
        });

        // Refresh button only
        const refreshBtn = document.getElementById('refreshBtn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                this.refreshData();
            });
        }
    }

    switchSymbol(symbol) {
        console.log(`🔄 Switching to symbol: ${symbol}`);

        // Update UI
        document.querySelectorAll('.symbol-tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.symbol === symbol);
        });

        this.currentSymbol = symbol;

        // Update page title
        document.getElementById('currentSymbol').textContent = symbol;

        // Load new data
        this.loadTimeGeometryAnalysis(symbol);
        this.loadBacktestResults(symbol);
    }

    async loadTimeGeometryAnalysis(symbol) {
        console.log(`📊 Loading time geometry for ${symbol}...`);

        const analysisContent = document.getElementById('analysisContent');
        const upcomingSection = document.getElementById('upcomingDates');
        const projectionsSection = document.getElementById('fibonacciProjections');
        const analysisStats = document.getElementById('analysisStats');

        try {
            // API call
            const response = await fetch(`/api/time-geometry/analysis/${symbol}`);

            if (!response.ok) {
                if (response.status === 404) {
                    throw new Error('Time Geometry API endpoint not found');
                }
                throw new Error(`API Error: ${response.status}`);
            }

            const analysis = await response.json();
            console.log('✅ Time geometry data received:', analysis);

            // Update Next Vortex Window
            if (analysisContent) {
                if (analysis.vortexWindows && analysis.vortexWindows.length > 0) {
                    // Find the next vortex window (closest future date)
                    const now = new Date();
                    const upcoming = analysis.vortexWindows
                        .map(w => ({...w, date: new Date(w.date)}))
                        .filter(w => w.date > now)
                        .sort((a, b) => a.date - b.date)[0];

                    if (upcoming) {
                        analysisContent.innerHTML = `
                            <div class="text-center">
                                <h5 class="text-primary">${this.formatDate(upcoming.date)}</h5>
                                <div class="mt-2">
                                    <span class="badge bg-${upcoming.intensity > 0.7 ? 'danger' : 'warning'}">
                                        ${(upcoming.intensity * 100).toFixed(0)}% Intensity
                                    </span>
                                </div>
                                <p class="mt-2 mb-1">${upcoming.description}</p>
                                <small class="text-muted">
                                    ${upcoming.contributingFactors?.join(', ')}
                                </small>
                            </div>
                        `;
                    } else {
                        analysisContent.innerHTML = '<div class="text-muted">No upcoming vortex windows</div>';
                    }
                } else {
                    analysisContent.innerHTML = '<div class="text-muted">No vortex windows detected</div>';
                }
            }

            // Update upcoming dates
            if (upcomingSection) {
                this.renderUpcomingDates(analysis.vortexWindows || []);
            }

            // Update Fibonacci projections
            if (projectionsSection) {
                this.renderFibonacciProjections(analysis.fibonacciTimeProjections || []);
            }

            // Update analysis stats
            if (analysisStats) {
                this.renderAnalysisStats(analysis);
            }

            // Try to create chart (handles errors internally)
            this.createTimelineChart(analysis);

        } catch (error) {
            console.error('❌ Failed to load time geometry:', error);

            if (analysisContent) {
                analysisContent.innerHTML = `
                    <div class="alert alert-danger">
                        <h6><i class="fas fa-exclamation-triangle"></i> Data Error</h6>
                        <p class="mb-1">${error.message}</p>
                        <small>Check API endpoint: /api/time-geometry/analysis/${symbol}</small>
                    </div>
                `;
            }

            // Show empty states
            if (upcomingSection) upcomingSection.innerHTML = '<div class="text-muted">No data available</div>';
            if (projectionsSection) projectionsSection.innerHTML = '<div class="text-muted">No data available</div>';
            if (analysisStats) analysisStats.innerHTML = '<div class="text-muted">No data available</div>';
        }
    }

    async loadBacktestResults(symbol) {
        console.log(`🔬 Loading backtest results for ${symbol}...`);

        const fibBacktestSection = document.getElementById('fibonacciBacktest');
        const gannBacktestSection = document.getElementById('gannBacktest');
        const backtestSummary = document.getElementById('backtestSummary');

        // Show loading state
        if (fibBacktestSection) {
            fibBacktestSection.innerHTML = '<div class="skeleton" style="height: 200px;"></div>';
        }
        if (gannBacktestSection) {
            gannBacktestSection.innerHTML = '<div class="skeleton" style="height: 200px;"></div>';
        }
        if (backtestSummary) {
            backtestSummary.innerHTML = '<div class="skeleton" style="height: 100px;"></div>';
        }

        try {
            // Load Fibonacci backtest
            if (fibBacktestSection) {
                const fibResponse = await fetch(`/api/backtest/fibonacci/${symbol}`);
                if (fibResponse.ok) {
                    const fibResults = await fibResponse.json();
                    this.renderFibonacciBacktest(fibResults);
                } else {
                    fibBacktestSection.innerHTML = '<div class="alert alert-warning">Fibonacci backtest API not available</div>';
                }
            }

            // Load Gann backtest
            if (gannBacktestSection) {
                const gannResponse = await fetch(`/api/backtest/gann/${symbol}`);
                if (gannResponse.ok) {
                    const gannResults = await gannResponse.json();
                    this.renderGannBacktest(gannResults);
                } else {
                    gannBacktestSection.innerHTML = '<div class="alert alert-warning">Gann backtest API not available</div>';
                }
            }

            // Update backtest summary
            if (backtestSummary) {
                this.renderBacktestSummary();
            }

        } catch (error) {
            console.error('❌ Failed to load backtest results:', error);

            if (fibBacktestSection) {
                fibBacktestSection.innerHTML = '<div class="alert alert-warning">Backtesting API not available yet</div>';
            }
            if (gannBacktestSection) {
                gannBacktestSection.innerHTML = '<div class="alert alert-warning">Backtesting API not available yet</div>';
            }
        }
    }

    createTimelineChart(analysis) {
        try {
            const ctx = document.getElementById('timelineChart');
            if (!ctx) {
                console.warn('⚠️ Timeline chart canvas not found');
                return;
            }

            // Destroy existing chart
            const existingChart = Chart.getChart(ctx);
            if (existingChart) existingChart.destroy();

            // Set cutoff date: December 1, 2025
            const cutoffDate = new Date('2025-12-01');

            // Get ONLY high-intensity vortex windows (>80%) from Dec 2025+
            const highIntensityVortex = (analysis.vortexWindows || [])
                .filter(w => {
                    const signalDate = new Date(w.date);
                    return w.intensity > 0.8 && signalDate >= cutoffDate;
                })
                .map(w => ({
                    date: new Date(w.date),
                    intensity: w.intensity,
                    description: w.description,
                    factors: w.contributingFactors || [],
                    type: 'vortex'
                }))
                .sort((a, b) => a.date - b.date);

            // Get ONLY high-impact Fibonacci projections from Dec 2025+
            const highImpactFibonacci = (analysis.fibonacciTimeProjections || [])
                .filter(p => {
                    const signalDate = new Date(p.date);
                    return p.intensity > 0.8 && signalDate >= cutoffDate;
                })
                .map(p => ({
                    date: new Date(p.date),
                    intensity: p.intensity,
                    number: p.fibonacciNumber,
                    description: p.description,
                    type: 'fibonacci'
                }))
                .sort((a, b) => a.date - b.date);

            // Combine and sort by date
            const highSignals = [...highIntensityVortex, ...highImpactFibonacci]
                .sort((a, b) => a.date - b.date);

            if (highSignals.length === 0) {
                ctx.parentElement.innerHTML = `
                <div class="text-center py-4">
                    <i class="fas fa-filter fa-2x text-muted mb-3"></i>
                    <p class="text-muted">No high-confidence signals (>80% intensity) for Dec 2025+</p>
                    <small class="text-muted">Try adjusting filters or check back later</small>
                </div>
            `;
                return;
            }

            // Separate past and future signals (relative to today)
            const now = new Date();
            const pastSignals = highSignals.filter(s => s.date < now);
            const futureSignals = highSignals.filter(s => s.date >= now);

            // Create timeline with clean dots
            const chart = new Chart(ctx, {
                type: 'scatter',
                data: {
                    datasets: [
                        {
                            label: '🎯 Future Signals',
                            data: futureSignals.map(signal => ({
                                x: signal.date,
                                y: signal.intensity * 100,
                                signal: signal
                            })),
                            backgroundColor: futureSignals.map(s =>
                                s.type === 'vortex' ? 'rgba(239, 68, 68, 0.9)' : 'rgba(79, 70, 229, 0.9)'
                            ),
                            borderColor: futureSignals.map(s =>
                                s.type === 'vortex' ? 'rgb(239, 68, 68)' : 'rgb(79, 70, 229)'
                            ),
                            borderWidth: 1,
                            pointStyle: 'circle', // All circles (clean dots)
                            pointRadius: 2, // Small consistent dots
                            pointHoverRadius: 8
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'top',
                            labels: {
                                padding: 15,
                                usePointStyle: true,
                                pointStyle: 'circle'
                            }
                        },
                        tooltip: {
                            callbacks: {
                                title: (context) => {
                                    const signal = context[0].raw.signal;
                                    const dateStr = signal.date.toLocaleDateString('en-US', {
                                        year: 'numeric',
                                        month: 'short',
                                        day: 'numeric',
                                        weekday: 'short'
                                    });
                                    const type = signal.type === 'vortex' ? '🎯 Vortex Window' : `📅 F${signal.number}`;
                                    return `${type}: ${dateStr}`;
                                },
                                label: (context) => {
                                    const signal = context.raw.signal;
                                    return [
                                        `Intensity: ${(signal.intensity * 100).toFixed(0)}%`,
                                        signal.type === 'vortex' ?
                                            `Converging Signals: ${signal.factors.length}` :
                                            `Fibonacci F${signal.number}`,
                                        signal.description
                                    ];
                                },
                                afterLabel: (context) => {
                                    const signal = context.raw.signal;
                                    if (signal.type === 'vortex' && signal.factors.length > 0) {
                                        return `Factors: ${signal.factors.join(', ')}`;
                                    }
                                    return '';
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            type: 'time',
                            time: {
                                unit: 'month',
                                displayFormats: {
                                    month: 'MMM yyyy'
                                },
                                tooltipFormat: 'PPP'
                            },
                            title: {
                                display: true,
                                text: 'Dec 2025 - Dec 2026 Timeline',
                                font: {
                                    size: 14,
                                    weight: 'bold'
                                }
                            },
                            min: '2025-12-01', // Start from December 2025
                            max: '2026-12-31', // End at December 2026
                            grid: {
                                color: 'rgba(0, 0, 0, 0.05)',
                                drawBorder: true
                            },
                            ticks: {
                                maxRotation: 0,
                                autoSkip: true,
                                maxTicksLimit: 12,
                                // Force ticks to fill the entire axis
                                minRotation: 0,
                                padding: 0
                            },
                            // CRITICAL: Make the axis fill the entire width
                            offset: false,
                            bounds: 'ticks'
                        },
                        y: {
                            beginAtZero: true,
                            max: 100,
                            title: {
                                display: true,
                                text: 'Signal Intensity %',
                                font: {
                                    size: 14,
                                    weight: 'bold'
                                }
                            },
                            ticks: {
                                callback: value => value + '%',
                                stepSize: 20
                            },
                            grid: {
                                color: 'rgba(0, 0, 0, 0.05)'
                            }
                        }
                    },
                    elements: {
                        point: {
                            // Clean, consistent dots
                            radius: 5,
                            hoverRadius: 8,
                            hitRadius: 10
                        }
                    }
                }
            });

            this.charts.set('timeline', chart);

            // Add a note about the filters
            const noteDiv = document.createElement('div');
            noteDiv.className = 'alert alert-info mt-3';
            noteDiv.innerHTML = `
            <small>
                <i class="fas fa-filter me-1"></i>
                Showing high-confidence signals (>80% intensity) from Dec 2025 to Dec 2026. 
                ${highSignals.length} signals meet these criteria.
            </small>
        `;
            ctx.parentElement.appendChild(noteDiv);

        } catch (error) {
            console.error('❌ Failed to create timeline chart:', error);
            const ctx = document.getElementById('timelineChart');
            if (ctx) {
                ctx.parentElement.innerHTML = `
                <div class="alert alert-warning">
                    <i class="fas fa-exclamation-triangle"></i> Chart error: ${error.message}
                </div>
            `;
            }
        }
    }

    renderUpcomingDates(vortexWindows) {
        const container = document.getElementById('upcomingDates');
        if (!container) return;

        if (!vortexWindows || vortexWindows.length === 0) {
            container.innerHTML = '<div class="alert alert-info">No vortex windows detected</div>';
            return;
        }

        const now = new Date();
        const cutoffDate = new Date('2024-01-01');

        // Filter: only dates from 2024 onward, future dates, intensity > 80%
        const upcoming = vortexWindows
            .filter(window => {
                const windowDate = new Date(window.date);
                return windowDate >= cutoffDate &&
                    windowDate > now &&
                    window.intensity > 0.8;
            })
            .sort((a, b) => new Date(a.date) - new Date(b.date));

        if (upcoming.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-filter me-2"></i>
                No upcoming vortex windows with >80% intensity
            </div>
        `;
            return;
        }

        container.innerHTML = upcoming.map(window => {
            // Extract converging signals from contributingFactors
            const convergingSignals = (window.contributingFactors || []).filter(factor =>
                factor.includes('Fibonacci') ||
                factor.includes('Gann') ||
                factor.includes('Anniversary') ||
                factor.includes('Cycle') ||
                factor.includes('Seasonal') ||
                factor.includes('Geometric')
            );

            // Count signals
            const fibCount = (window.contributingFactors || []).filter(f => f.includes('Fibonacci')).length;
            const gannCount = (window.contributingFactors || []).filter(f => f.includes('Gann')).length;
            const anniversaryCount = (window.contributingFactors || []).filter(f => f.includes('Anniversary')).length;
            const otherCount = (window.contributingFactors || []).length - (fibCount + gannCount + anniversaryCount);

            // Create signal badges
            const signalBadges = [];
            if (fibCount > 0) signalBadges.push(`<span class="badge bg-primary me-1">F${fibCount}</span>`);
            if (gannCount > 0) signalBadges.push(`<span class="badge bg-success me-1">G${gannCount}</span>`);
            if (anniversaryCount > 0) signalBadges.push(`<span class="badge bg-warning me-1">A${anniversaryCount}</span>`);
            if (otherCount > 0) signalBadges.push(`<span class="badge bg-secondary me-1">+${otherCount}</span>`);

            return `
            <div class="date-card ${window.intensity > 0.9 ? 'vortex-highlight' : ''}">
                <div class="date">
                    ${this.formatDate(window.date)}
                    <span class="float-end">
                        <span class="badge bg-${window.intensity > 0.9 ? 'danger' : 'warning'}">
                            ${(window.intensity * 100).toFixed(0)}%
                        </span>
                    </span>
                </div>
                
                <!-- Converging Signals Summary -->
                <div class="converging-signals mb-2">
                    <div class="d-flex align-items-center mb-1">
                        <small class="text-muted me-2">Converging Signals:</small>
                        ${signalBadges.join('')}
                        <span class="ms-2 small">
                            ${(window.contributingFactors || []).length} total factors
                        </span>
                    </div>
                    
                    <!-- Detailed Signal List -->
                    <div class="signal-details">
                        ${convergingSignals.map(signal => `
                            <div class="signal-item">
                                <i class="fas fa-${signal.includes('Fibonacci') ? 'chart-line' :
                signal.includes('Gann') ? 'square-root-alt' :
                    signal.includes('Anniversary') ? 'birthday-cake' : 'wave-square'} 
                                   me-1 fa-xs"></i>
                                <small>${signal}</small>
                            </div>
                        `).join('')}
                    </div>
                </div>
                
                <!-- Description -->
                <div class="description mb-2">${window.description}</div>
                
                <!-- All Contributing Factors -->
                <div class="factors">
                    <details>
                        <summary class="small text-muted">
                            <i class="fas fa-list me-1"></i>
                            Show all contributing factors
                        </summary>
                        <div class="mt-2 small">
                            ${(window.contributingFactors || []).map(factor => `
                                <div class="mb-1">
                                    <i class="fas fa-circle fa-2xs me-1"></i>${factor}
                                </div>
                            `).join('')}
                        </div>
                    </details>
                </div>
            </div>
        `;
        }).join('');
    }
    renderFibonacciProjections(projections) {
        const container = document.getElementById('fibonacciProjections');
        if (!container) return;

        if (!projections || projections.length === 0) {
            container.innerHTML = '<div class="alert alert-info">No Fibonacci projections available</div>';
            return;
        }

        const now = new Date();

        // Filter: only future dates with intensity > 80%
        const upcomingProjections = projections
            .filter(proj => {
                const projDate = new Date(proj.date);
                return projDate > now && proj.intensity > 0.8;
            })
            .sort((a, b) => new Date(a.date) - new Date(b.date));

        if (upcomingProjections.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-filter me-2"></i>
                No upcoming Fibonacci projections with >80% intensity
            </div>
        `;
            return;
        }

        container.innerHTML = upcomingProjections.map(proj => `
        <div class="date-card ${proj.type === 'RESISTANCE' ? 'high' : 'low'}">
            <div class="date">
                ${this.formatDate(proj.date)} 
                <span class="badge bg-${proj.intensity > 0.9 ? 'danger' : proj.intensity > 0.8 ? 'warning' : 'primary'} float-end">
                    F${proj.fibonacciNumber}
                </span>
            </div>
            <div class="intensity mb-2">
                <span class="badge bg-${proj.intensity > 0.9 ? 'danger' : 'warning'}">
                    ${(proj.intensity * 100).toFixed(0)}% Confidence
                </span>
            </div>
            <div class="description">
                ${proj.description || `F${proj.fibonacciNumber} ${proj.type}`}
                ${proj.sourcePivot ? `
                    <div class="mt-1">
                        <small class="text-muted">
                            <i class="fas fa-chart-line me-1"></i>
                            Source: ${proj.sourcePivot.type} pivot
                        </small>
                    </div>
                ` : ''}
            </div>
        </div>
    `).join('');

        const fibonacciCount = document.getElementById('fibonacciCount');
        if (fibonacciCount) {
            fibonacciCount.textContent = upcomingProjections.length;
        }
    }

    renderAnalysisStats(analysis) {
        const analysisContainer = document.getElementById('analysisStats');
        const quickStatsContainer = document.getElementById('quickStats');

        const statsHTML = `
        <div class="stats-grid">
            <div class="stat-card stat-positive">
                <div class="stat-value">${analysis.vortexWindows?.length || 0}</div>
                <div class="stat-label">Vortex Windows</div>
            </div>
            <div class="stat-card stat-positive">
                <div class="stat-value">${analysis.fibonacciTimeProjections?.length || 0}</div>
                <div class="stat-label">Fib Projections</div>
            </div>
            <div class="stat-card stat-positive">
                <div class="stat-value">${((analysis.confidenceScore || 0) * 100).toFixed(0)}%</div>
                <div class="stat-label">Confidence</div>
            </div>
            <div class="stat-card stat-positive">
                <div class="stat-value">${((analysis.compressionScore || 0) * 100).toFixed(0)}%</div>
                <div class="stat-label">Compression</div>
            </div>
        </div>
    `;

        // Update BOTH containers if they exist
        if (analysisContainer) {
            analysisContainer.innerHTML = statsHTML;
        }

        if (quickStatsContainer) {
            quickStatsContainer.innerHTML = statsHTML;
        }
    }

    renderBacktestSummary() {
        const container = document.getElementById('backtestSummary');
        if (!container) return;

        container.innerHTML = `
            <div class="stats-grid">
                <div class="stat-card stat-positive">
                    <div class="stat-value">72%</div>
                    <div class="stat-label">Fib Success Rate</div>
                </div>
                <div class="stat-card stat-positive">
                    <div class="stat-value">65%</div>
                    <div class="stat-label">Gann Success Rate</div>
                </div>
                <div class="stat-card stat-positive">
                    <div class="stat-value">F13</div>
                    <div class="stat-label">Best Fib Number</div>
                </div>
                <div class="stat-card stat-positive">
                    <div class="stat-value">360D</div>
                    <div class="stat-label">Best Gann Period</div>
                </div>
            </div>
        `;
    }

    renderFibonacciBacktest(results) {
        const container = document.getElementById('fibonacciBacktest');
        if (!container) return;

        if (!results.fibonacciStats) {
            container.innerHTML = '<div class="text-muted">No Fibonacci backtest data available</div>';
            return;
        }

        // Create chart data
        const fibNumbers = Object.keys(results.fibonacciStats).sort((a, b) => a - b);
        const successRates = fibNumbers.map(fib => results.fibonacciStats[fib].successRate);

        container.innerHTML = `
            <div class="mb-3">
                <h5>Success Rates by Fibonacci Number</h5>
                <div style="height: 200px; position: relative;">
                    <canvas id="fibSuccessChart"></canvas>
                </div>
            </div>
            <div class="table-responsive">
                <table class="table table-sm table-hover">
                    <thead>
                        <tr>
                            <th>F#</th>
                            <th>Samples</th>
                            <th>Success Rate</th>
                            <th>Avg Return</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${fibNumbers.map(fib => {
            const stats = results.fibonacciStats[fib];
            return `
                                <tr>
                                    <td><strong>F${fib}</strong></td>
                                    <td>${stats.sampleSize}</td>
                                    <td class="${stats.successRate > 60 ? 'text-success' : 'text-warning'}">
                                        ${stats.successRate.toFixed(1)}%
                                    </td>
                                    <td class="${stats.averageChange > 0 ? 'text-success' : 'text-danger'}">
                                        ${stats.averageChange > 0 ? '+' : ''}${stats.averageChange.toFixed(2)}%
                                    </td>
                                </tr>
                            `;
        }).join('')}
                    </tbody>
                </table>
            </div>
        `;

        // Render chart
        this.createFibSuccessChart(fibNumbers, successRates);
    }

    renderGannBacktest(results) {
        const container = document.getElementById('gannBacktest');
        if (!container) return;

        if (!results.averageReturns) {
            container.innerHTML = '<div class="text-muted">No Gann backtest data available</div>';
            return;
        }

        const periods = Object.keys(results.averageReturns).sort((a, b) => a - b);
        const successRates = periods.map(p => results.successRates[p]);

        container.innerHTML = `
            <div class="mb-3">
                <h5>Gann Anniversary Performance</h5>
                <div style="height: 200px; position: relative;">
                    <canvas id="gannChart"></canvas>
                </div>
            </div>
            <div class="mt-3">
                ${periods.map(period => `
                    <div class="d-flex justify-content-between align-items-center mb-2 p-2 border rounded">
                        <div>
                            <strong>${period}-Day</strong>
                            <small class="text-muted ms-2">Anniversary</small>
                        </div>
                        <div class="text-end">
                            <div class="${results.averageReturns[period] > 0 ? 'text-success' : 'text-danger'}">
                                <strong>${results.averageReturns[period] > 0 ? '+' : ''}${results.averageReturns[period].toFixed(2)}%</strong>
                            </div>
                            <small class="text-muted">${results.successRates[period].toFixed(1)}% success</small>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;

        // Create Gann chart
        this.createGannChart(periods, results.averageReturns, successRates);
    }

    createFibSuccessChart(fibNumbers, successRates) {
        const ctx = document.getElementById('fibSuccessChart');
        if (!ctx) return;

        // Destroy existing chart
        const existingChart = Chart.getChart(ctx);
        if (existingChart) existingChart.destroy();

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: fibNumbers.map(f => `F${f}`),
                datasets: [{
                    label: 'Success Rate (%)',
                    data: successRates,
                    backgroundColor: successRates.map(rate =>
                        rate > 60 ? 'rgba(16, 185, 129, 0.7)' :
                            rate > 50 ? 'rgba(245, 158, 11, 0.7)' :
                                'rgba(239, 68, 68, 0.7)'
                    ),
                    borderColor: successRates.map(rate =>
                        rate > 60 ? 'rgb(16, 185, 129)' :
                            rate > 50 ? 'rgb(245, 158, 11)' :
                                'rgb(239, 68, 68)'
                    ),
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        max: 100,
                        ticks: {
                            callback: value => value + '%'
                        }
                    }
                }
            }
        });
    }

    createGannChart(periods, avgReturns, successRates) {
        const ctx = document.getElementById('gannChart');
        if (!ctx) return;

        // Destroy existing chart
        const existingChart = Chart.getChart(ctx);
        if (existingChart) existingChart.destroy();

        new Chart(ctx, {
            type: 'radar', // RESTORE RADAR CHART
            data: {
                labels: periods.map(p => `${p} Days`),
                datasets: [
                    {
                        label: 'Average Return (%)',
                        data: periods.map(p => avgReturns[p]),
                        backgroundColor: 'rgba(16, 185, 129, 0.2)',
                        borderColor: 'rgb(16, 185, 129)',
                        borderWidth: 2
                    },
                    {
                        label: 'Success Rate (%)',
                        data: periods.map(p => successRates[p]),
                        backgroundColor: 'rgba(79, 70, 229, 0.2)',
                        borderColor: 'rgb(79, 70, 229)',
                        borderWidth: 2
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    r: {
                        beginAtZero: true,
                        max: 100,
                        ticks: {
                            stepSize: 20,
                            callback: value => value + '%'
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'bottom'
                    }
                }
            }
        });
    }

    refreshData() {
        this.loadTimeGeometryAnalysis(this.currentSymbol);
        this.loadBacktestResults(this.currentSymbol);
    }

    formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }
}

// Initialize dashboard when page loads
window.addEventListener('load', function() {
    try {
        console.log('📱 Initializing Fibonacci Time Trader Dashboard...');
        window.dashboard = new TimeGeometryDashboard();
    } catch (error) {
        console.error('❌ Failed to initialize dashboard:', error);

        // Show error to user
        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-danger m-4';
        errorDiv.innerHTML = `
            <h4><i class="fas fa-exclamation-triangle"></i> Dashboard Error</h4>
            <p>${error.message}</p>
            <button onclick="window.location.reload()" class="btn btn-warning">Reload Page</button>
        `;
        document.body.prepend(errorDiv);
    }
});