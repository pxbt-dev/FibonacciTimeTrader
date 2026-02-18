console.log('=== APP.JS LOADING ===');
console.log('Backtest section exists?', !!document.getElementById('backtestingSection'));
console.log('Backtest results container exists?', !!document.getElementById('backtestResults'));

// ===== BACKTEST DISPLAY FUNCTIONS =====

async function loadAndDisplayBacktestData() {
    console.log('📥 Loading backtest data...');

    const container = document.getElementById('backtestResults');
    if (!container) {
        console.error('❌ backtestResults container not found!');
        return;
    }

    // Show loading state
    container.innerHTML = `
        <div class="backtest-loading">
            <div class="spinner-backtest"></div>
            <p class="mt-3">Loading historical performance data...</p>
        </div>
    `;

    try {
        // Load all backtest data
        const [fibonacciData, gannData, confluenceData] = await Promise.all([
            fetchBacktestData('fibonacci', 'BTC'),
            fetchBacktestData('gann', 'BTC'),
            fetchBacktestData('confluence', 'BTC')
        ]);

        console.log('✅ Backtest data loaded:', {
            fibonacci: fibonacciData,
            gann: gannData,
            confluence: confluenceData
        });

        // Display the data
        renderBacktestResults(fibonacciData, gannData, confluenceData);

    } catch (error) {
        console.error('❌ Failed to load backtest data:', error);
        showBacktestError(error.message);
    }
}

async function fetchBacktestData(type, symbol) {
    console.log(`📡 Fetching ${type} backtest for ${symbol}...`);

    try {
        const response = await fetch(`/api/backtest/${type}/${symbol}`);

        if (!response.ok) {
            throw new Error(`${type} API error: ${response.status}`);
        }

        return await response.json();

    } catch (error) {
        console.error(`Failed to fetch ${type} data:`, error);

        // Return empty data structure
        return {
            symbol: symbol,
            error: error.message,
            isEmpty: true
        };
    }
}

function renderBacktestResults(fibonacciData, gannData, confluenceData) {
    const container = document.getElementById('backtestResults');

    container.innerHTML = `
        <div class="row">
            <!-- Fibonacci Results -->
            <div class="col-lg-4 col-md-6 mb-4">
                <div class="glass-card h-100">
                    <h4><i class="fas fa-calculator me-2 text-primary"></i>Fibonacci Performance</h4>
                    <p class="text-white small mb-3">BTC Historical Results</p>
                    ${renderFibonacciSection(fibonacciData)}
                </div>
            </div>
            
            <!-- Gann Results -->
            <div class="col-lg-4 col-md-6 mb-4">
                <div class="glass-card h-100">
                    <h4><i class="fas fa-bullseye me-2 text-success"></i>Gann Cycle Performance</h4>
                    <p class="text-white small mb-3">BTC Historical Results</p>
                    ${renderGannSection(gannData)}
                </div>
            </div>
            
            <!-- Confluence Results -->
            <div class="col-lg-4 col-md-12 mb-4">
                <div class="glass-card h-100">
                    <h4><i class="fas fa-calendar-check me-2 text-warning"></i>Confluence Windows</h4>
                    <p class="text-white small mb-3">BTC Historical Results</p>
                    ${renderConfluenceSection(confluenceData)}
                </div>
            </div>
        </div>
        
        <!-- Summary -->
        <div class="row">
            <div class="col-md-12">
                <div class="glass-card">
                    <h5><i class="fas fa-chart-bar me-2"></i>Performance Summary</h5>
                    <div class="row text-center">
                        <div class="col-md-4">
                            <div class="metric-value ${getMetricColor(calculateAverageSuccess(fibonacciData))}">
                                ${calculateAverageSuccess(fibonacciData).toFixed(1)}%
                            </div>
                            <div class="metric-label">Fibonacci Success</div>
                        </div>
                        <div class="col-md-4">
                            <div class="metric-value ${getMetricColor(calculateAverageSuccess(gannData))}">
                                ${calculateAverageSuccess(gannData).toFixed(1)}%
                            </div>
                            <div class="metric-label">Gann Success</div>
                        </div>
                        <div class="col-md-4">
                            <div class="metric-value ${getMetricColor(getConfluence7DayRate(confluenceData))}">
                                ${getConfluence7DayRate(confluenceData).toFixed(1)}%
                            </div>
                            <div class="metric-label">7-Day Confluence</div>
                        </div>
                    </div>
                    <div class="mt-3 text-center">
                        <button class="btn btn-sm btn-outline-info" onclick="refreshBacktestData()">
                            <i class="fas fa-redo me-1"></i>Refresh Data
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function renderFibonacciSection(data) {
    if (data.error || data.isEmpty) {
        return `
            <div class="text-center py-4">
                <i class="fas fa-exclamation-triangle fa-2x text-warning mb-3"></i>
                <p class="text-white">${data.error || 'No data available'}</p>
            </div>
        `;
    }

    const stats = data.fibonacciStats || {};

    if (Object.keys(stats).length === 0) {
        return `
            <div class="text-center py-4">
                <p class="text-white">No Fibonacci data available</p>
            </div>
        `;
    }

    // Get top 3 ratios
    const topRatios = Object.entries(stats)
        .sort((a, b) => (b[1].successRate || 0) - (a[1].successRate || 0))
        .slice(0, 3);

    const rows = topRatios.map(([ratio, stat]) => `
        <div class="d-flex justify-content-between align-items-center mb-2 p-2 bg-dark rounded">
            <div>
                <strong>${ratio}</strong>
                <div class="small text-white">${getRatioType(parseFloat(ratio))}</div>
            </div>
            <div class="text-end">
                <div class="${(stat.successRate || 0) >= 60 ? 'text-success' : (stat.successRate || 0) >= 50 ? 'text-warning' : 'text-danger'}">
                    <strong>${stat.successRate ? stat.successRate.toFixed(1) + '%' : 'N/A'}</strong>
                </div>
                <div class="small text-white">${stat.sampleSize || 0} samples</div>
            </div>
        </div>
    `).join('');

    return `
        <div class="mb-3">
            <div class="small text-white mb-2">Top Performing Ratios:</div>
            ${rows}
        </div>
        <div class="small text-white">
            <i class="fas fa-info-circle me-1"></i>
            ${Object.values(stats).reduce((sum, stat) => sum + (stat.sampleSize || 0), 0)} tests
        </div>
    `;
}

function renderGannSection(data) {
    if (data.error || data.isEmpty) {
        return `
            <div class="text-center py-4">
                <i class="fas fa-exclamation-triangle fa-2x text-warning mb-3"></i>
                <p class="text-white">${data.error || 'No data available'}</p>
            </div>
        `;
    }

    const successRates = data.successRates || {};

    if (Object.keys(successRates).length === 0) {
        return `
            <div class="text-center py-4">
                <p class="text-white">No Gann data available</p>
            </div>
        `;
    }

    // Get top 3 cycles
    const topCycles = Object.entries(successRates)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 3);

    const rows = topCycles.map(([period, rate]) => {
        const avgReturn = data.averageReturns?.[parseInt(period)] || 0;
        return `
            <div class="d-flex justify-content-between align-items-center mb-2 p-2 bg-dark rounded">
                <div>
                    <strong>${period} Days</strong>
                    <div class="small text-white">Gann Cycle</div>
                </div>
                <div class="text-end">
                    <div class="${rate >= 60 ? 'text-success' : rate >= 50 ? 'text-warning' : 'text-danger'}">
                        <strong>${rate.toFixed(1)}%</strong>
                    </div>
                    <div class="small ${avgReturn > 0 ? 'text-success' : 'text-danger'}">
                        ${avgReturn.toFixed(2)}% avg return
                    </div>
                </div>
            </div>
        `;
    }).join('');

    return `
        <div class="mb-3">
            <div class="small text-white mb-2">Top Performing Cycles:</div>
            ${rows}
        </div>
        <div class="small text-white">
            <i class="fas fa-info-circle me-1"></i>
            ${Object.keys(successRates).length} cycles analyzed
        </div>
    `;
}

function renderConfluenceSection(data) {
    if (data.error || data.isEmpty) {
        return `
            <div class="text-center py-4">
                <i class="fas fa-exclamation-triangle fa-2x text-warning mb-3"></i>
                <p class="text-white">${data.error || 'No data available'}</p>
            </div>
        `;
    }

    // Check if we have the nested structure
    const performance = data.confluencePerformance || data;
    const successRates = performance.successRates || {};

    if (Object.keys(successRates).length === 0) {
        return `
            <div class="text-center py-4">
                <p class="text-white">No confluence data available</p>
            </div>
        `;
    }

    const timeframes = [1, 7, 30];

    const rows = timeframes.map(days => {
        const rate = successRates[days] || 0;
        const avgReturn = performance.averageReturns?.[days] || 0;
        const samples = performance.sampleSizes?.[days] || 0;

        return `
            <div class="d-flex justify-content-between align-items-center mb-2">
                <div>
                    <span class="badge bg-secondary">${days}D</span>
                    <span class="ms-2">${days === 1 ? 'Next Day' : days + ' Days'}</span>
                </div>
                <div class="text-end">
                    <div class="${rate >= 60 ? 'text-success' : rate >= 50 ? 'text-warning' : 'text-danger'}">
                        <strong>${rate.toFixed(1)}%</strong>
                    </div>
                    <div class="small ${avgReturn > 0 ? 'text-success' : 'text-danger'}">
                        ${avgReturn > 0 ? '+' : ''}${avgReturn.toFixed(2)}%
                    </div>
                </div>
            </div>
        `;
    }).join('');

    return `
        <div class="mb-3">
            <div class="small text-white mb-2">Success Rates:</div>
            ${rows}
        </div>
        <div class="small text-white">
            <i class="fas fa-info-circle me-1"></i>
            ${performance.totalWindows || 'N/A'} windows tested
        </div>
    `;
}

// Helper functions
function calculateAverageSuccess(data) {
    if (data.fibonacciStats) {
        const stats = Object.values(data.fibonacciStats);
        if (stats.length === 0) return 0;
        const avg = stats.reduce((sum, stat) => sum + (stat.successRate || 0), 0) / stats.length;
        return avg;
    }
    if (data.successRates) {
        const rates = Object.values(data.successRates);
        if (rates.length === 0) return 0;
        return rates.reduce((sum, rate) => sum + rate, 0) / rates.length;
    }
    return 0;
}

function getConfluence7DayRate(data) {
    const performance = data.confluencePerformance || data;
    return performance.successRates?.[7] || 0;
}

function getMetricColor(value) {
    if (value >= 70) return 'text-success';
    if (value >= 50) return 'text-warning';
    return 'text-danger';
}

function getRatioType(ratio) {
    const harmonic = [0.333, 0.667, 1.333, 1.667, 2.333, 2.667];
    const geometric = [1.5, 2.5, 3.5];
    if (harmonic.some(r => Math.abs(r - ratio) < 0.001)) return 'Harmonic';
    if (geometric.some(r => Math.abs(r - ratio) < 0.001)) return 'Geometric';
    if (ratio === 2.0) return 'Double';
    if (ratio === 3.0) return 'Triple';
    return 'Fibonacci';
}

function showBacktestError(message) {
    const container = document.getElementById('backtestResults');
    container.innerHTML = `
        <div class="alert alert-danger">
            <h5><i class="fas fa-exclamation-triangle me-2"></i>Backtest Error</h5>
            <p>${message}</p>
            <div class="mt-2">
                <button class="btn btn-sm btn-outline-warning" onclick="loadAndDisplayBacktestData()">
                    <i class="fas fa-redo me-1"></i>Try Again
                </button>
            </div>
        </div>
    `;
}

// Make refresh function available globally
window.refreshBacktestData = function() {
    console.log('🔄 Refreshing backtest data...');
    loadAndDisplayBacktestData();
};

console.log('✅ Backtest display ready - will load automatically');

// ===== MAIN DASHBOARD CLASS =====
class TimeGeometryDashboard {
    constructor() {
        this.currentSymbol = 'BTC';
        this.charts = new Map();
        this.solarData = null;
        this.lastUpdated = new Date();
    }

    // Switch between symbols
    switchSymbol(symbol) {
        console.log(`🔄 Switching to symbol: ${symbol}`);

        // Update UI
        document.querySelectorAll('.symbol-tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.symbol === symbol);
        });

        this.currentSymbol = symbol;

        // Update page title
        document.getElementById('currentSymbol').textContent = symbol;

        // Update last updated time
        this.lastUpdated = new Date();
        this.updateTimestamp();

        // Load new data
        this.loadAllData(symbol);
    }

// Load ALL data (time geometry + solar + lunar) in parallel
    async loadAllData(symbol) {
        console.log(`📊 Loading ALL data for ${symbol}...`);

        try {
            const controller1 = new AbortController();
            const controller2 = new AbortController();
            const controller3 = new AbortController();
            const timeoutId = setTimeout(() => {
                controller1.abort();
                controller2.abort();
                controller3.abort();
            }, 15000);

            // FIX: Add lunarResponse to destructuring
            const [timeGeometryResponse, solarResponse, lunarResponse] = await Promise.all([
                fetch(`/api/timeGeometry/analysis/${symbol}`, {
                    signal: controller1.signal
                }),
                fetch('/api/solar/forecast', {
                    signal: controller2.signal
                }),
                fetch('/api/lunar/events', {
                    signal: controller3.signal
                })
            ]);

            clearTimeout(timeoutId);

            if (!timeGeometryResponse.ok) {
                throw new Error(`Time Geometry API Error: ${timeGeometryResponse.status}`);
            }

            const analysis = await timeGeometryResponse.json();

            // Load solar data if available
            if (solarResponse.ok) {
                this.solarData = await solarResponse.json();
            } else {
                console.warn('Solar API failed, continuing without solar data');
                this.solarData = null;
            }

            // Load lunar data if available
            if (lunarResponse.ok) {
                this.lunarData = await lunarResponse.json();
                console.log('✅ Lunar data loaded:', this.lunarData.length, 'events');
            } else {
                console.warn('Lunar API failed, continuing without lunar data');
                this.lunarData = null;
            }

            console.log('✅ ALL data loaded');

            // Update all UI components in order
            this.createTimelineChart(analysis);
            this.renderAlignmentDates(analysis.vortexWindows || []);
            this.renderFibonacciLevels(analysis.fibonacciPriceLevels || []);
            this.loadGannDates(symbol);
            this.renderSolarLunarDashboard();

            console.log('📊 Analysis data:', {
                vortexWindows: analysis.vortexWindows?.length || 0,
                fibonacciPriceLevels: analysis.fibonacciPriceLevels?.length || 0,
                lunarEvents: this.lunarData?.length || 0
            });

        } catch (error) {
            console.error('❌ Failed to load ALL data:', error);
            this.showError(error);
        }
    }

    // Load Gann dates with proper time range
    async loadGannDates(symbol) {
        const container = document.getElementById('gannDates');
        if (!container) return;

        console.log(`📅 Loading Gann dates for ${symbol}...`);

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/api/gann/dates/${symbol}`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`API Error: ${response.status}`);
            }

            const gannDates = await response.json();
            console.log(`✅ Gann dates received: ${gannDates.length} dates`);

            // Log all dates for debugging
            gannDates.forEach(gann => {
                console.log(`📅 Gann Date: ${gann.date}, Type: ${gann.type}, Source: ${gann.sourcePivot?.date}`);
            });

            this.renderGannDatesList(gannDates);

        } catch (error) {
            console.error('❌ Failed to load Gann dates:', error);
            container.innerHTML = `
                <div class="alert alert-warning">
                    <i class="fas fa-exclamation-triangle me-2"></i>
                    Gann dates temporarily unavailable
                    <div class="small mt-1">${error.message}</div>
                </div>
            `;
        }
    }

    // Render Gann dates including future dates
    renderGannDatesList(gannDates) {
        const container = document.getElementById('gannDates');
        if (!container) return;

        if (!Array.isArray(gannDates) || gannDates.length === 0) {
            container.innerHTML = `
                <div class="alert alert-info">
                    <i class="fas fa-calendar-check me-2"></i>
                    No Gann dates found
                </div>
            `;
            return;
        }

        const now = new Date();

        // Filter future dates and sort
        const futureDates = gannDates
            .filter(gann => {
                const gannDate = new Date(gann.date);
                return gannDate > now;
            })
            .sort((a, b) => new Date(a.date) - new Date(b.date))
            .slice(0, 15); // Show more dates

        if (futureDates.length === 0) {
            container.innerHTML = `
                <div class="alert alert-info">
                    <i class="fas fa-calendar-check me-2"></i>
                    No upcoming Gann dates
                </div>
            `;
            return;
        }

        container.innerHTML = futureDates.map(gann => {
            const period = gann.type ? gann.type.replace('D_ANNIVERSARY', 'D') : 'Gann';
            const sourcePivot = gann.sourcePivot || {};
            const isRecent = this.isRecentDate(gann.date, 30);

            return `
                <div class="date-card gann-date-card ${isRecent ? 'border-warning' : ''}">
                    <div class="date">
                        ${this.formatDate(gann.date)}
                        <span class="badge bg-success float-end">
                            ${period}
                        </span>
                    </div>
                    
                    ${sourcePivot.date ? `
                        <div class="mb-2">
                            <span class="badge ${sourcePivot.type && sourcePivot.type.includes('HIGH') ? 'bg-danger' : 'bg-success'}">
                                ${sourcePivot.type || 'Pivot'}
                            </span>
                            <span class="badge bg-secondary ms-1">
                                $${sourcePivot.price ? this.formatPrice(sourcePivot.price) : 'N/A'}
                            </span>
                        </div>
                        
                        <div class="description small text-white">
                            <div class="mb-1">
                                <i class="fas fa-arrow-right me-1"></i>
                                From ${this.formatDate(sourcePivot.date)}
                            </div>
                            <div class="mt-1">
                                <span class="badge bg-dark">
                                    ${this.daysFromNow(gann.date)}
                                </span>
                            </div>
                        </div>
                    ` : `
                        <div class="description text-white">
                            Gann ${period.toLowerCase()} anniversary
                            <div class="mt-1">
                                <span class="badge bg-dark">
                                    ${this.daysFromNow(gann.date)}
                                </span>
                            </div>
                        </div>
                    `}
                </div>
            `;
        }).join('');
    }

    // Render Fibonacci levels separated by type
    renderFibonacciLevels(priceLevels) {
        const resistanceContainer = document.getElementById('resistanceLevels');
        const supportContainer = document.getElementById('supportLevels');

        if (!priceLevels || priceLevels.length === 0) {
            resistanceContainer.innerHTML = '<div class="alert alert-info">No resistance levels</div>';
            supportContainer.innerHTML = '<div class="alert alert-info">No support levels</div>';
            return;
        }

        // Separate levels by type
        const supportLevels = priceLevels.filter(level => level.type === 'SUPPORT');
        const resistanceLevels = priceLevels.filter(level => level.type === 'RESISTANCE');

        // Helper function to determine ratio type class
        const getRatioClass = (ratio) => {
            // Use tolerance for floating point comparison
            const isApprox = (a, b) => Math.abs(a - b) < 0.001;

            if (isApprox(ratio, 0.333) || isApprox(ratio, 0.667) ||
                isApprox(ratio, 1.333) || isApprox(ratio, 1.667) ||
                isApprox(ratio, 2.333) || isApprox(ratio, 2.667) ||
                isApprox(ratio, 3.333) || isApprox(ratio, 3.667) ||
                isApprox(ratio, 4.333)) {
                return 'harmonic';
            } else if (isApprox(ratio, 1.5) || isApprox(ratio, 2.5) ||
                isApprox(ratio, 3.5) || isApprox(ratio, 4.5)) {
                return 'geometric';
            } else if (isApprox(ratio, 2.0)) {
                return 'double';
            } else if (isApprox(ratio, 3.0)) {
                return 'triple';
            } else if (isApprox(ratio, 4.0)) {
                return 'quadruple';
            } else {
                return 'fibonacci';
            }
        };

        // Helper function to determine badge class based on ratio type
        const getRatioBadgeClass = (ratio) => {
            const ratioClass = getRatioClass(ratio);
            switch(ratioClass) {
                case 'harmonic': return 'badge-harmonic';
                case 'geometric': return 'badge-geometric';
                case 'double': return 'badge-double';
                case 'triple': return 'badge-triple';
                case 'quadruple': return 'badge-quadruple';
                default: return 'bg-secondary';
            }
        };

        // Render resistance levels (LEFT COLUMN)
        if (resistanceLevels.length > 0) {
            // Sort resistance by price (lowest to highest)
            resistanceLevels.sort((a, b) => a.price - b.price);

            resistanceContainer.innerHTML = resistanceLevels.map(level => {
                const formattedPrice = `$${this.formatPrice(level.price)}`;
                const ratioClass = getRatioClass(level.ratio);
                const badgeClass = getRatioBadgeClass(level.ratio);
                const isKeyLevel = level.ratio === 1.618 || level.ratio === 2.618 ||
                    level.ratio === 2.0 || level.ratio === 3.0;

                // Get ratio type label
                const ratioTypeLabel = ratioClass === 'harmonic' ? 'Harmonic' :
                    ratioClass === 'geometric' ? 'Geometric' :
                        ratioClass === 'double' ? 'Double' :
                            ratioClass === 'triple' ? 'Triple' :
                                ratioClass === 'quadruple' ? 'Quadruple' : 'Fibonacci';

                return `
            <div class="date-card ${ratioClass} ${isKeyLevel ? 'key-level' : ''}">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <div>
                        <span class="badge ${badgeClass}">
                            ${ratioTypeLabel}: ${level.ratio.toFixed(3)}
                        </span>
                        ${isKeyLevel ? '<span class="badge bg-warning ms-1">Key</span>' : ''}
                    </div>
                    <div class="h5 mb-0 text-success">
                        ${formattedPrice}
                    </div>
                </div>
                <div class="description">
                    <i class="fas fa-arrow-up me-1 text-success"></i>
                    ${level.distanceFromHigh} above cycle high
                    <div class="mt-1 small">
                        <span class="badge ${badgeClass}">
                            ${level.label}
                        </span>
                    </div>
                </div>
            </div>
            `;
            }).join('');
        } else {
            resistanceContainer.innerHTML = '<div class="alert alert-info">No resistance levels available</div>';
        }

        // Render support levels (MIDDLE COLUMN)
        if (supportLevels.length > 0) {
            // Sort support by price (highest to lowest)
            supportLevels.sort((a, b) => b.price - a.price);

            supportContainer.innerHTML = supportLevels.map(level => {
                const formattedPrice = `$${this.formatPrice(level.price)}`;
                const ratioClass = getRatioClass(level.ratio);
                const badgeClass = getRatioBadgeClass(level.ratio);
                const isKeyLevel = level.ratio === 0.618 || level.ratio === 0.786 ||
                    level.ratio === 0.333 || level.ratio === 0.667 ||
                    level.ratio === 0.5;

                // Get ratio type label
                const ratioTypeLabel = ratioClass === 'harmonic' ? 'Harmonic' :
                    ratioClass === 'geometric' ? 'Geometric' : 'Fibonacci';

                return `
            <div class="date-card ${ratioClass} ${isKeyLevel ? 'key-level' : ''}">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <div>
                        <span class="badge ${badgeClass}">
                            ${ratioTypeLabel}: ${level.ratio.toFixed(3)}
                        </span>
                        ${isKeyLevel ? '<span class="badge bg-info ms-1">Key</span>' : ''}
                        ${level.ratio === 0 ? '<span class="badge bg-dark ms-1">Cycle High</span>' : ''}
                        ${level.ratio === 1.0 ? '<span class="badge bg-dark ms-1">Cycle Low</span>' : ''}
                    </div>
                    <div class="h5 mb-0 text-danger">
                        ${formattedPrice}
                    </div>
                </div>
                <div class="description">
                    <i class="fas fa-arrow-down me-1 text-danger"></i>
                    ${level.distanceFromHigh} from cycle high
                    <div class="mt-1 small">
                        <span class="badge ${badgeClass}">
                            ${level.label}
                        </span>
                    </div>
                </div>
            </div>
            `;
            }).join('');
        } else {
            supportContainer.innerHTML = '<div class="alert alert-info">No support levels available</div>';
        }
    }

    // FIXED: Enhanced alignment dates rendering WITHOUT expandable dropdown
    renderAlignmentDates(alignmentDates) {
        const container = document.getElementById('upcomingDates');
        if (!container) return;

        if (!alignmentDates || alignmentDates.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-calendar-check me-2"></i>
                No Fibonacci/Gann alignment dates detected
            </div>
        `;
            return;
        }

        // Group by date
        const datesMap = {};
        alignmentDates.forEach(alignment => {
            const dateKey = alignment.date;
            if (!datesMap[dateKey]) {
                datesMap[dateKey] = {
                    date: alignment.date,
                    alignments: [],
                    maxIntensity: alignment.intensity
                };
            }
            datesMap[dateKey].alignments.push(alignment);
            datesMap[dateKey].maxIntensity = Math.max(datesMap[dateKey].maxIntensity, alignment.intensity);
        });

        // Convert to array and sort by date
        const groupedDates = Object.values(datesMap).sort((a, b) =>
            new Date(a.date) - new Date(b.date)
        );

        // Filter to future dates only
        const now = new Date();
        const futureDates = groupedDates.filter(group => {
            const date = new Date(group.date);
            return date > now;
        });

        if (futureDates.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-calendar-check me-2"></i>
                No upcoming alignment dates
            </div>
        `;
            return;
        }

        // Clear container and create row
        container.innerHTML = '';

        // Create cards in a grid layout
        futureDates.forEach((group, index) => {
            const col = document.createElement('div');
            col.className = 'col-lg-4 col-md-6 mb-3';

            const date = new Date(group.date);
            const hasMultiple = group.alignments.length > 1;

            // Get all unique signals from all alignments on this date
            const allSignals = [];
            group.alignments.forEach(alignment => {
                const factors = alignment.contributingFactors || [];
                const signals = this.parseConvergingSignals(factors);
                allSignals.push(...signals);
            });

            // Remove duplicates and categorize
            const uniqueSignals = Array.from(new Set(allSignals.map(s => s.label)))
                .map(label => allSignals.find(s => s.label === label));

            const fibSignals = uniqueSignals.filter(s => s.type === 'fibonacci');
            const gannSignals = uniqueSignals.filter(s => s.type === 'gann');
            const otherSignals = uniqueSignals.filter(s => s.type === 'other');

            col.innerHTML = `
    <div class="date-card ${group.maxIntensity > 0.8 ? 'vortex-highlight' : ''}">
        <div class="date">
            ${this.formatDate(group.date)}
            <span class="float-end">
                <span class="badge ${group.maxIntensity > 0.8 ? 'bg-danger' : group.maxIntensity > 0.5 ? 'bg-warning' : 'bg-primary'}">
                    ${(group.maxIntensity * 100).toFixed(0)}%
                </span>
            </span>
        </div>
        
        <div class="converging-signals mb-2">
            <div class="d-flex align-items-center mb-1">
                <small class="text-white me-2">Signals:</small>
                ${uniqueSignals.map(signal =>
                `<span class="badge ${signal.type === 'fibonacci' ? 'bg-primary' : signal.type === 'gann' ? 'bg-success' : 'bg-warning'} me-1 mb-1">${signal.label}</span>`
            ).join('')}
            </div>
        </div>
        
        <div class="description mb-2">
            <div class="small text-white">
                ${this.daysFromNow(group.date)}
            </div>
        </div>
        
        <!-- Only show expandable for multiple alignments (different timeframes) -->
        ${hasMultiple ? `
            <div class="additional-alignments mt-2">
                <button class="btn btn-sm btn-outline-info btn-toggle w-100" 
                        onclick="this.nextElementSibling.classList.toggle('d-none'); this.querySelector('i').classList.toggle('fa-rotate-90')">
                    <i class="fas fa-chevron-right fa-xs"></i>
                    Show ${group.alignments.length - 1} additional alignment${group.alignments.length > 2 ? 's' : ''}
                </button>
                <div class="d-none mt-2">
                    ${group.alignments.slice(1).map((alignment, idx) => {
                const signals = this.parseConvergingSignals(alignment.contributingFactors || []);
                return `
                            <div class="additional-alignment small mb-2 p-2 bg-dark rounded">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        ${signals.map(s =>
                    `<span class="badge ${s.type === 'fibonacci' ? 'bg-primary' : s.type === 'gann' ? 'bg-success' : 'bg-warning'} me-1">${s.label}</span>`
                ).join('')}
                                    </div>
                                    <span class="badge ${alignment.intensity > 0.8 ? 'bg-danger' : alignment.intensity > 0.5 ? 'bg-warning' : 'bg-info'}">
                                        ${(alignment.intensity * 100).toFixed(0)}%
                                    </span>
                                </div>
                                ${alignment.description ? `<div class="text-white mt-1 small">${alignment.description}</div>` : ''}
                            </div>
                        `;
            }).join('')}
                </div>
            </div>
        ` : ''}
    </div>
`;

            container.appendChild(col);
        });
    }

    // Create timeline chart with ALL Gann dates
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

            // Cutoff date: December 1, 2025
            const cutoffDate = new Date('2026-02-01T00:00:00Z');
            const endDate = new Date('2027-02-28T00:00:00Z');

            // Get solar data if available
            const solarHighApDays = this.solarData?.forecast || [];

            // Get lunar data if available
            const lunarEvents = this.lunarData || [];

            // Process lunar events for timeline
            const lunarTimelineEvents = lunarEvents
                .filter(event => {
                    if (!event.date) return false;
                    const eventDate = new Date(event.date);
                    return eventDate >= cutoffDate && eventDate <= endDate;
                })
                .map(event => {
                    const date = new Date(event.date);
                    // Set intensity based on event type
                    let intensity = 75;
                    let icon = '🌙';

                    if (event.eventType === 'FULL_MOON') {
                        intensity = 90;
                        icon = '🌕';
                    } else if (event.eventType === 'NEW_MOON') {
                        intensity = 85;
                        icon = '🌑';
                    }

                    return {
                        x: date,
                        y: intensity,
                        type: 'lunar',
                        event: event,
                        eventType: event.eventType || 'UNKNOWN',
                        eventName: event.eventName || 'Lunar Event',
                        icon: icon,
                        lunation: event.lunation
                    };
                })
                .sort((a, b) => a.x - b.x);

            const allAlignmentDates = analysis.vortexWindows || [];
            const allFibonacciProjections = analysis.fibonacciTimeProjections || [];

            // Load Gann dates separately
            this.loadGannDataForChart().then(gannDates => {
                console.log('📊 Gann dates for chart:', gannDates.length);
                console.log('📊 Lunar events for chart:', lunarTimelineEvents.length);

                // Process alignment dates
                const processedAlignments = allAlignmentDates
                    .filter(alignment => {
                        const signalDate = new Date(alignment.date);
                        return signalDate >= cutoffDate && signalDate <= endDate;
                    })
                    .map(alignment => {
                        const factors = alignment.contributingFactors || [];
                        const hasGann = factors.some(f => f.includes('_ANNIVERSARY'));
                        const hasFibonacci = factors.some(f => f.startsWith('FIB_'));
                        const hasLunar = factors.some(f => f.includes('LUNAR_'));

                        if (hasGann) {
                            return {
                                x: new Date(alignment.date),
                                y: alignment.intensity * 100,
                                type: 'gann_alignment',
                                alignment: alignment
                            };
                        } else if (hasFibonacci) {
                            const fibSignals = factors.filter(f => f.startsWith('FIB_'));
                            const ratioLabels = fibSignals.map(f => {
                                const fibNumber = parseInt(f.replace('FIB_', ''));
                                const ratio = fibNumber / 100.0;
                                return this.getFibRatioLabel(ratio, fibNumber);
                            });

                            return {
                                x: new Date(alignment.date),
                                y: alignment.intensity * 100,
                                type: 'fib_alignment',
                                alignment: alignment,
                                ratioLabels: ratioLabels
                            };
                        } else if (hasLunar) {
                            const lunarSignals = factors.filter(f => f.includes('LUNAR_'));
                            return {
                                x: new Date(alignment.date),
                                y: alignment.intensity * 100,
                                type: 'lunar_alignment',
                                alignment: alignment,
                                lunarSignals: lunarSignals
                            };
                        }
                        return null;
                    })
                    .filter(item => item !== null);

                const standaloneFibonacci = allFibonacciProjections
                    .filter(p => {
                        const signalDate = new Date(p.date);
                        return signalDate >= cutoffDate && signalDate <= endDate;
                    })
                    .map(p => {
                        const ratio = p.fibonacciRatio || (p.fibonacciNumber / 100.0);
                        const days = p.fibonacciNumber;
                        const label = p.fibLabel || this.getFibRatioLabel(ratio, days);

                        return {
                            x: new Date(p.date),
                            y: p.intensity * 100,
                            type: 'fibonacci',
                            projection: p,
                            ratio: ratio,
                            ratioLabel: label,
                            days: days
                        };
                    })
                    .sort((a, b) => a.x - b.x);

                // Process solar events
                const solarTimelineEvents = solarHighApDays
                    .filter(day => {
                        if (!day.date || day.ap < 12) return false;
                        const eventDate = new Date(day.date + 'T00:00:00Z');
                        return eventDate >= cutoffDate && eventDate <= endDate;
                    })
                    .map(day => {
                        const date = new Date(day.date + 'T12:00:00Z');
                        return {
                            x: date,
                            y: 95,
                            ap: day.ap,
                            type: 'solar',
                            event: { date: day.date, ap: day.ap }
                        };
                    })
                    .sort((a, b) => a.x - b.x);

                // Combine all signals
                const allSignals = [
                    ...gannDates,
                    ...processedAlignments,
                    ...standaloneFibonacci,
                    ...solarTimelineEvents,
                    ...lunarTimelineEvents
                ].sort((a, b) => a.x - b.x);

                console.log('📊 Total signals for chart:', {
                    gann: gannDates.length,
                    alignments: processedAlignments.length,
                    fibonacci: standaloneFibonacci.length,
                    solar: solarTimelineEvents.length,
                    lunar: lunarTimelineEvents.length,
                    total: allSignals.length
                });

                if (allSignals.length === 0) {
                    ctx.parentElement.innerHTML = `
                    <div class="text-center py-4">
                        <i class="fas fa-filter fa-2x text-white mb-3"></i>
                        <p class="text-white">No signals for Dec 2025+</p>
                    </div>
                `;
                    return;
                }

                // Separate past and future signals
                const now = new Date();
                const futureSignals = allSignals.filter(s => s.x >= now);

                // Group signals by type
                const gannData = futureSignals.filter(s => s.type === 'gann' || s.type === 'gann_alignment');
                const fibData = futureSignals.filter(s => s.type === 'fibonacci' || s.type === 'fib_alignment');
                const solarData = solarTimelineEvents.filter(s => s.x >= now);
                const lunarData = lunarTimelineEvents.filter(s => s.x >= now);

                // Create the chart
                this.createChart(ctx, gannData, fibData, solarData, lunarData, futureSignals.length);
            }).catch(error => {
                console.error('Failed to load Gann data for chart:', error);
            });

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

    // Helper to load Gann data for chart
    async loadGannDataForChart() {
        try {
            const response = await fetch(`/api/gann/dates/${this.currentSymbol}`);
            if (!response.ok) return [];

            const gannDates = await response.json();

            return gannDates
                .filter(gann => {
                    const gannDate = new Date(gann.date);
                    const cutoffDate = new Date('2025-12-01T00:00:00Z');
                    const endDate = new Date('2026-12-31T00:00:00Z');
                    return gannDate >= cutoffDate && gannDate <= endDate;
                })
                .map(gann => ({
                    x: new Date(gann.date),
                    y: 85, // Intensity for standalone Gann dates
                    type: 'gann',
                    gann: gann,
                    period: gann.type ? gann.type.replace('D_ANNIVERSARY', 'D') : 'Gann'
                }));
        } catch (error) {
            console.error('Failed to load Gann data for chart:', error);
            return [];
        }
    }

    // Helper to create the chart
// Helper to create the chart with lunar dataset
    createChart(ctx, gannData, fibData, solarData, lunarData, totalSignals) {
        new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [
                    {
                        label: `📅 Gann Dates (${gannData.length})`,
                        data: gannData.map(signal => ({
                            x: signal.x,
                            y: signal.y,
                            signal: signal
                        })),
                        backgroundColor: 'rgba(16, 185, 129, 0.8)',
                        borderColor: 'rgb(16, 185, 129)',
                        borderWidth: 1,
                        pointStyle: 'circle',
                        pointRadius: 4,
                        pointHoverRadius: 9
                    },
                    {
                        label: `🔷 Fibonacci (${fibData.length})`,
                        data: fibData.map(signal => ({
                            x: signal.x,
                            y: signal.y,
                            signal: signal
                        })),
                        backgroundColor: 'rgba(79, 70, 229, 0.8)',
                        borderColor: 'rgb(79, 70, 229)',
                        borderWidth: 1,
                        pointStyle: 'circle',
                        pointRadius: 3.5,
                        pointHoverRadius: 8
                    },
                    {
                        label: `☀️ Solar AP ≥ 12 (${solarData.length})`,
                        data: solarData.map(signal => ({
                            x: signal.x,
                            y: signal.y,
                            signal: signal
                        })),
                        backgroundColor: 'rgba(245, 158, 11, 1)',
                        borderColor: 'rgb(245, 158, 11)',
                        borderWidth: 2,
                        pointStyle: 'circle',
                        pointRadius: 3,
                        pointHoverRadius: 7
                    },
                    {
                        label: `🌙 Lunar (${lunarData.length})`,
                        data: lunarData.map(signal => ({
                            x: signal.x,
                            y: signal.y,
                            signal: signal
                        })),
                        backgroundColor: 'rgba(147, 51, 234, 0.8)',
                        borderColor: 'rgb(147, 51, 234)',
                        borderWidth: 1,
                        pointStyle: 'circle',
                        pointRadius: 3,
                        pointHoverRadius: 7
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
                            color: '#f1f5f9',
                            font: {
                                size: 12
                            }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(15, 23, 42, 0.9)',
                        titleColor: '#f1f5f9',
                        bodyColor: '#f1f5f9',
                        borderColor: '#334155',
                        borderWidth: 1,
                        callbacks: {
                            title: (context) => {
                                const point = context[0].raw;
                                const signal = point.signal;
                                return signal.x.toLocaleDateString('en-US', {
                                    year: 'numeric',
                                    month: 'short',
                                    day: 'numeric',
                                    weekday: 'short'
                                });
                            },
                            label: (context) => {
                                const point = context.raw;
                                const signal = point.signal;
                                return this.getTooltipLabel(signal);
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
                            color: '#f1f5f9',
                            font: {
                                size: 14,
                                weight: 'bold'
                            }
                        },
                        min: '2026-02-01',
                        max: '2027-02-28',
                        grid: {
                            color: 'rgba(255, 255, 255, 0.05)'
                        },
                        ticks: {
                            color: '#94a3b8',
                            maxRotation: 0,
                            autoSkip: true,
                            maxTicksLimit: 12
                        }
                    },
                    y: {
                        beginAtZero: true,
                        max: 100,
                        title: {
                            display: true,
                            text: 'Signal Intensity %',
                            color: '#f1f5f9',
                            font: {
                                size: 14,
                                weight: 'bold'
                            }
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.05)'
                        },
                        ticks: {
                            color: '#94a3b8'
                        }
                    }
                }
            }
        });
    }

    // Helper for tooltip labels
    getTooltipLabel(signal) {
        switch(signal.type) {
            case 'gann':
            case 'gann_alignment':
                if (signal.alignment) {
                    const gannFactors = signal.alignment.contributingFactors || [];
                    const gannSignals = gannFactors.filter(f => f.includes('_ANNIVERSARY'));
                    return [
                        `Gann Anniversary${gannSignals.length > 1 ? 's' : ''}`,
                        `Signals: ${gannSignals.length}`,
                        ...gannSignals.map(f => f.replace('_ANNIVERSARY', 'D'))
                    ];
                } else {
                    return [
                        `Gann ${signal.period || 'Anniversary'}`,
                        `From: ${signal.gann?.sourcePivot?.type || 'pivot'}`,
                        `Source: ${signal.gann?.sourcePivot?.date || 'N/A'}`
                    ];
                }
            case 'fibonacci':
                const fib = signal.projection || signal;
                const sourceDate = fib.sourcePivot?.date ?
                    new Date(fib.sourcePivot.date).toLocaleDateString('en-US', {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric'
                    }) : 'Unknown pivot';

                return [
                    `${signal.ratioLabel || 'Fibonacci'}`,
                    `From: ${sourceDate}`,
                    `${signal.days || fib.fibonacciNumber || 0} days`,
                    `Type: ${fib.type || 'TIME_PROJECTION'}`,
                    `Intensity: ${Math.round(signal.y)}%`
                ];
            case 'fib_alignment':
                if (signal.ratioLabels) {
                    return [
                        `Fibonacci Alignment`,
                        `Signals: ${signal.ratioLabels.length}`,
                        ...signal.ratioLabels
                    ];
                }
                return ['Fibonacci Alignment'];
            case 'solar':
                return [
                    `Solar Geomagnetic Activity`,
                    `AP Index: ${signal.event.ap}`,
                    `High AP Day (≥12)`
                ];
            case 'lunar':
            case 'lunar_alignment':
                if (signal.alignment && signal.lunarSignals) {
                    return [
                        `Lunar Alignment`,
                        `Signals: ${signal.lunarSignals.length}`,
                        ...signal.lunarSignals.map(f => f.replace('LUNAR_', ''))
                    ];
                } else {
                    return [
                        `${signal.icon || '🌙'} ${signal.eventName}`,
                        signal.lunation ? `Lunation: ${signal.lunation}` : '',
                        signal.eventType,
                        signal.event?.details || ''
                    ];
                }
            default:
                return [`Signal: ${signal.type}`];
        }
    }

    // Render solar dashboard with dark theme
    renderSolarLunarDashboard() {
        const container = document.getElementById('solarDashboard');
        if (!container || !this.solarData) {
            container.innerHTML = `
            <div class="alert alert-warning">
                <i class="fas fa-exclamation-triangle me-2"></i>
                Solar data not available
            </div>
        `;
            return;
        }

        const highApDays = this.solarData.forecast || [];
        const currentAp = this.solarData.currentAp || 0;
        const issueDate = this.solarData.issueDate || 'Unknown';

        container.innerHTML = `
        <div class="row mb-4">
            <div class="col-md-4 mb-3">
                <div class="card bg-dark border-${currentAp >= 12 ? 'warning' : 'success'}">
                    <div class="card-body text-center">
                        <div class="display-4 fw-bold text-${currentAp >= 12 ? 'warning' : 'success'}">${currentAp}</div>
                        <div class="text-uppercase small text-white">Current AP Index</div>
                        <div class="mt-2">
                            <span class="badge ${currentAp >= 12 ? 'bg-warning' : 'bg-success'}">
                                ${currentAp >= 12 ? 'Elevated' : 'Normal'}
                            </span>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="col-md-4 mb-3">
                <div class="card bg-dark border-info">
                    <div class="card-body text-center">
                        <div class="display-4 fw-bold text-info">${highApDays.length}</div>
                        <div class="text-uppercase small text-white">High AP Days (≥12)</div>
                        <div class="mt-2">
                            <span class="badge ${highApDays.length > 0 ? 'bg-warning' : 'bg-success'}">
                                ${highApDays.length > 0 ? 'Active Period' : 'Quiet'}
                            </span>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="col-md-4 mb-3">
                <div class="card bg-dark border-secondary text-white">
                    <div class="card-body text-center">
                        <div class="h2 mb-2 text-info">
                            <i class="fas fa-satellite"></i>
                        </div>
                        <div class="text-uppercase small text-white">Forecast Source</div>
                        <div class="mt-2 small">
                            NOAA 45-day<br>${issueDate}
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        ${highApDays.length > 0 ? `
            <div class="alert ${highApDays.length > 5 ? 'alert-warning' : 'alert-info'}">
                <i class="fas ${highApDays.length > 5 ? 'fa-exclamation-triangle' : 'fa-info-circle'} me-2"></i>
                <strong>${highApDays.length} days with AP ≥ 12 detected</strong>
                <div class="mt-2 small">
                    Next high AP day: ${this.formatDate(highApDays[0]?.date)} (${this.daysFromNow(highApDays[0]?.date)})
                </div>
            </div>
            
            <div class="mt-4">
                <h5><i class="fas fa-list me-2"></i>High AP Dates (AP ≥ 12)</h5>
                <div class="table-responsive">
                    <table class="table table-dark table-sm">
                        <thead>
                            <tr>
                                <th>Date</th>
                                <th>AP Index</th>
                                <th>Days From Now</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${highApDays.slice(0, 10).map(day => `
                                <tr>
                                    <td>${this.formatDate(day.date)}</td>
                                    <td><span class="badge ${day.ap >= 20 ? 'bg-danger' : 'bg-warning'}">${day.ap}</span></td>
                                    <td>${this.daysFromNow(day.date)}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                ${highApDays.length > 10 ? `<div class="text-center small text-white mt-2">+ ${highApDays.length - 10} more days</div>` : ''}
            </div>
        ` : `
            <div class="alert alert-success">
                <i class="fas fa-check-circle me-2"></i>
                <strong>Quiet geomagnetic period</strong>
                <div class="mt-1 small">No days with AP ≥ 12 in the forecast</div>
            </div>
        `}
    `;
    }

    parseConvergingSignals(factors) {
        const signals = [];

        factors.forEach((factor) => {
            // Skip raw format signals - we only want clean labels
            if (factor.startsWith('HARMONIC_FIB_') ||
                factor.startsWith('GANN_') ||
                factor === 'Triple 3.000') {
                return; // Skip these raw/internal formats
            }

            if (factor.startsWith('FIB_')) {
                const fibStr = factor.replace('FIB_', '');
                let ratio, days;

                if (fibStr.includes('.')) {
                    ratio = parseFloat(fibStr);
                    days = Math.round(ratio * 100);
                } else {
                    days = parseInt(fibStr);
                    ratio = days / 100.0;
                }

                signals.push({
                    type: 'fibonacci',
                    label: this.getFibRatioLabel(ratio, days),
                    number: days,
                    ratio: ratio,
                    days: days
                });
            }
            else if (factor.includes('_ANNIVERSARY')) {
                const days = factor.replace('_ANNIVERSARY', '').replace('D', '');
                signals.push({
                    type: 'gann',
                    label: `Gann ${days}D`,
                    days: parseInt(days)
                });
            }
            else if (factor.includes('LUNAR_')) {
                const lunarType = factor.replace('LUNAR_', '');
                let label = 'Lunar Event';

                if (lunarType === 'NEW_MOON') {
                    label = '🌑 New Moon';
                } else if (lunarType === 'FULL_MOON') {
                    label = '🌕 Full Moon';
                } else {
                    label = `Lunar ${lunarType}`;
                }

                signals.push({
                    type: 'lunar',
                    label: label,
                    lunarType: lunarType
                });
            }
            // Keep the clean harmonic labels we created
            else if (factor.includes(' + ')) {
                signals.push({
                    type: 'harmonic',
                    label: factor,
                    rawFactor: factor
                });
            }
        });

        return signals;
    }

    getFibRatioLabel(ratio, days) {
        console.log(`🔍 getFibRatioLabel called with: ratio=${ratio}, days=${days}`);

        // Apply rounding corrections
        const roundingCorrections = {
            79: 0.786,
            62: 0.618,
            38: 0.382,
            50: 0.500,
            127: 1.272,
            162: 1.618,
            262: 2.618,
            33: 0.333,    // Harmonic 1/3
            67: 0.667,    // Harmonic 2/3
            133: 1.333,   // Harmonic 1.333
            150: 1.500,   // Geometric 1.5
            167: 1.667,   // Harmonic 1.667
            200: 2.000,   // Double
            233: 2.333,   // Harmonic 2.333
            250: 2.500,   // Geometric 2.5
            267: 2.667,   // Harmonic 2.667
            300: 3.000    // Triple
        };

        // Apply correction if days match a known rounding case
        if (roundingCorrections[days]) {
            const correctRatio = roundingCorrections[days];
            const ratioDiff = Math.abs(ratio - correctRatio);
            if (ratioDiff > 0.002) {
                console.log(`🔄 Correcting: ${ratio.toFixed(3)} (${days}d) → ${correctRatio}`);
                ratio = correctRatio;
            }
        }

        // RATIO MAPPING with Harmonic/Geometric labels
        const ratioMap = {
            // Fibonacci
            0.382: 'Fib 0.382',
            0.500: 'Fib 0.500',
            0.618: 'Fib 0.618',
            0.786: 'Fib 0.786',
            1.000: 'Fib 1.000',
            1.272: 'Fib 1.272',
            1.618: 'Fib 1.618',
            2.618: 'Fib 2.618',

            // Harmonic/Geometric
            0.333: 'Harmonic 0.333',
            0.667: 'Harmonic 0.667',
            1.333: 'Harmonic 1.333',
            1.500: 'Geometric 1.500',
            1.667: 'Harmonic 1.667',
            2.000: 'Double 2.000',
            2.333: 'Harmonic 2.333',
            2.500: 'Geometric 2.500',
            2.667: 'Harmonic 2.667',
            3.000: 'Triple 3.000',
            3.333: 'Harmonic 3.333',
            3.500: 'Geometric 3.500',
            3.667: 'Harmonic 3.667',
            4.000: 'Quadruple 4.000',
            4.236: 'Fib 4.236',
            4.333: 'Harmonic 4.333',
            4.500: 'Geometric 4.500'
        };

        const roundedRatio = Math.round(ratio * 1000) / 1000;

        // Check exact matches
        if (ratioMap[roundedRatio]) {
            return ratioMap[roundedRatio];
        }

        // Check close matches
        for (const [key, label] of Object.entries(ratioMap)) {
            if (Math.abs(roundedRatio - parseFloat(key)) < 0.001) {
                return label;
            }
        }

        // Fallback - determine type
        if (Math.abs(ratio % (1/3)) < 0.001) {
            return `Harmonic ${roundedRatio.toFixed(3)}`;
        } else if (Math.abs(ratio % 0.5) < 0.001) {
            return `Geometric ${roundedRatio.toFixed(3)}`;
        } else {
            return `Fib ${roundedRatio.toFixed(3)}`;
        }
    }

    formatDate(dateString) {
        if (!dateString) return 'Unknown';
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }

    formatPrice(price) {
        return price.toLocaleString(undefined, {
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        });
    }

    daysFromNow(dateString) {
        if (!dateString) return '?';
        const date = new Date(dateString);
        const now = new Date();
        const diffTime = date - now;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (diffDays > 0) {
            return `${diffDays} days from today`;
        } else if (diffDays === 0) {
            return 'Today';
        } else {
            return `${Math.abs(diffDays)} days ago`;
        }
    }

    isRecentDate(dateString, daysThreshold = 30) {
        const date = new Date(dateString);
        const now = new Date();
        const diffTime = date - now;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
        return diffDays > 0 && diffDays <= daysThreshold;
    }

    updateTimestamp() {
        document.getElementById('lastUpdated').textContent =
            this.lastUpdated.toLocaleTimeString('en-US', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
    }

    showError(error) {
        const errorMessage = error.name === 'AbortError' ?
            'Request timeout: API took too long to respond' :
            error.message;

        const errorDiv = document.createElement('div');
        errorDiv.className = 'alert alert-danger mt-3';
        errorDiv.innerHTML = `
            <h6><i class="fas fa-exclamation-triangle"></i> Data Error</h6>
            <p class="mb-1">${errorMessage}</p>
            <small>Check API endpoints</small>
        `;

        const container = document.querySelector('.container-fluid');
        if (container) {
            container.prepend(errorDiv);
        }
    }

    // Refresh all data
    refreshData() {
        this.loadAllData(this.currentSymbol);
        this.updateTimestamp();
    }
}

async function debugDates() {
    const response = await fetch('/api/backtest/actual-events/BTC?lookbackDays=60');
    const data = await response.json();

    const now = new Date();
    console.log('Current date/time:', now.toISOString());
    console.log('Current year:', now.getFullYear());

    // Check the actual date strings
    const sampleDates = data.events?.slice(0, 10).map(e => {
        const dateObj = new Date(e.date);
        return {
            rawDate: e.date,
            parsedDate: dateObj.toISOString(),
            year: dateObj.getFullYear(),
            month: dateObj.getMonth() + 1,
            day: dateObj.getDate(),
            isFuture: dateObj > now
        };
    });

    console.log('Sample dates from API:', sampleDates);

    // Check what year we're actually in
    console.log('\n📅 Date interpretation issue:');
    console.log('If you see "Dec 17, 2025" but it\'s really 2024, there might be:');
    console.log('1. Date parsing issue (wrong year interpretation)');
    console.log('2. Timezone issue');
    console.log('3. Data with wrong year values');
}

debugDates();

async function analyzeEventPatterns() {
    const response = await fetch('/api/backtest/actual-events/BTC?lookbackDays=60');
    const data = await response.json();

    const events = data.events || [];

    // Count directions
    const directionCounts = events.reduce((acc, event) => {
        acc[event.direction] = (acc[event.direction] || 0) + 1;
        return acc;
    }, {});

    // Group by signal type
    const signalCounts = events.reduce((acc, event) => {
        acc[event.signalType] = (acc[event.signalType] || 0) + 1;
        return acc;
    }, {});

    // Check price range
    const prices = events.map(e => e.price).filter(p => p);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const avgPrice = prices.reduce((sum, p) => sum + p, 0) / prices.length;

    console.log('📊 Event Analysis:');
    console.log('Total events:', events.length);
    console.log('Direction distribution:', directionCounts);
    console.log('Signal type distribution:', signalCounts);
    console.log('Price range:', {
        min: `$${minPrice.toLocaleString()}`,
        max: `$${maxPrice.toLocaleString()}`,
        avg: `$${avgPrice.toLocaleString()}`,
        spread: `${((maxPrice - minPrice) / avgPrice * 100).toFixed(1)}%`
    });

    // Check if all Gann events are down
    const gannEvents = events.filter(e => e.signalType === 'GANN');
    const gannDirections = gannEvents.reduce((acc, event) => {
        acc[event.direction] = (acc[event.direction] || 0) + 1;
        return acc;
    }, {});

    console.log('\n🎯 Gann-specific analysis:');
    console.log('Total Gann events:', gannEvents.length);
    console.log('Gann direction distribution:', gannDirections);
    console.log('Down percentage:', `${((gannDirections.DOWN || 0) / gannEvents.length * 100).toFixed(1)}%`);

    // Check date range
    const dates = events.map(e => new Date(e.date));
    const sortedDates = dates.sort((a, b) => a - b);
    console.log('\n📅 Date range:');
    console.log('Earliest:', sortedDates[0].toLocaleDateString());
    console.log('Latest:', sortedDates[sortedDates.length - 1].toLocaleDateString());
    console.log('Span:', Math.round((sortedDates[sortedDates.length - 1] - sortedDates[0]) / (1000 * 60 * 60 * 24)), 'days');
}

analyzeEventPatterns();

async function testAPI() {
    const response = await fetch('/api/backtest/actual-events/BTC?lookbackDays=60');
    const data = await response.json();

    console.log('📊 API Response Structure:', {
        totalEvents: data.events?.length || 0,
        firstEvent: data.events?.[0],
        eventKeys: data.events?.[0] ? Object.keys(data.events[0]) : [],
        sampleDates: data.events?.slice(0, 5).map(e => e.date)
    });

    // Check if dates are future or past
    const now = new Date();
    const dates = data.events?.map(e => new Date(e.date)) || [];
    const futureDates = dates.filter(d => d > now).length;
    const pastDates = dates.filter(d => d <= now).length;

    console.log('📅 Date Analysis:', {
        futureDates,
        pastDates,
        ratio: `${((futureDates/(futureDates+pastDates))*100).toFixed(1)}% future dates`
    });
}

// Run it
testAPI();

// ===== MARKET EVENTS DISPLAY CLASS =====
// ===== MARKET EVENTS DISPLAY CLASS =====
// ===== MARKET EVENTS DISPLAY CLASS =====
// ===== MARKET EVENTS DISPLAY CLASS =====
class MarketEventsDisplay {
    constructor() {
        this.currentSymbol = 'BTC';
        this.currentFilter = 'ALL'; // ALL, UP, DOWN
        this.eventsData = [];
        this.gannCycles = [30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720];
        this.initialize();
    }

    initialize() {
        console.log('📈 Initializing Market Events Display...');

        // Set up event listeners
        document.getElementById('loadEventsBtn')?.addEventListener('click', () => this.loadEvents());

        // Set up filter buttons
        this.createFilterButtons();

        // Auto-load on page load
        setTimeout(() => this.loadEvents(), 1500);

        // Listen for symbol changes from main dashboard
        this.listenForSymbolChanges();
    }

    createFilterButtons() {
        const filterContainer = document.createElement('div');
        filterContainer.className = 'd-flex align-items-center ms-4';
        filterContainer.innerHTML = `
            <label class="me-3 text-white">Filter:</label>
            <div class="btn-group btn-group-sm" role="group" id="directionFilter">
                <button type="button" class="btn btn-outline-info active" data-filter="ALL">All</button>
                <button type="button" class="btn btn-outline-success" data-filter="UP">↑ Up</button>
                <button type="button" class="btn btn-outline-danger" data-filter="DOWN">↓ Down</button>
            </div>
        `;

        // Add to the existing controls row
        const controlsRow = document.querySelector('#marketEventsSection .row.mb-3 .col-md-8');
        if (controlsRow) {
            controlsRow.appendChild(filterContainer);

            // Add event listeners to filter buttons
            document.querySelectorAll('[data-filter]').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const filter = e.target.dataset.filter;
                    this.setFilter(filter);
                });
            });
        }

        // Add CSS for the filter buttons
        this.addFilterStyles();
    }

    addFilterStyles() {
        const style = document.createElement('style');
        style.textContent = `
            .bg-indigo {
                background-color: #6366f1 !important;
            }
            .bg-teal {
                background-color: #14b8a6 !important;
            }
            .bg-purple {
                background-color: #8b5cf6 !important;
            }
            .bg-amber {
                background-color: #f59e0b !important;
            }
            #directionFilter .btn.active {
                color: white !important;
            }
            #directionFilter .btn[data-filter="ALL"].active {
                background-color: #0dcaf0 !important;
                border-color: #0dcaf0 !important;
            }
            #directionFilter .btn[data-filter="UP"].active {
                background-color: #198754 !important;
                border-color: #198754 !important;
            }
            #directionFilter .btn[data-filter="DOWN"].active {
                background-color: #dc3545 !important;
                border-color: #dc3545 !important;
            }
            .move-summary {
                font-size: 0.9rem;
            }
            // .cycle-analysis {
            //     max-height: 300px;
            //     overflow-y: auto;
            // }
            .cycle-row:hover {
                background-color: rgba(255, 255, 255, 0.05);
            }
            .progress-thin {
                height: 6px;
            }
            .total-up {
                color: #198754;
                font-weight: bold;
            }
            .total-down {
                color: #dc3545;
                font-weight: bold;
            }
            .cycle-badge {
                min-width: 50px;
                text-align: center;
            }
            .success-rate-high {
                color: #10b981 !important;
                font-weight: bold;
            }
            .success-rate-medium {
                color: #f59e0b !important;
                font-weight: bold;
            }
            .success-rate-low {
                color: #ef4444 !important;
                font-weight: bold;
            }
            .bg-gold {
    background-color: #f59e0b !important;
    color: #000 !important;
    font-weight: bold;
}
.table-warning {
    background-color: rgba(245, 158, 11, 0.1) !important;
}
.table-warning:hover {
    background-color: rgba(245, 158, 11, 0.2) !important;
}
        `;
        document.head.appendChild(style);
    }

    setFilter(filter) {
        this.currentFilter = filter;

        // Update active button
        document.querySelectorAll('[data-filter]').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.filter === filter);
        });

        // Re-render events with filter
        if (this.eventsData.events) {
            this.renderEvents(this.eventsData);
        }
    }

    listenForSymbolChanges() {
        if (window.dashboard && window.dashboard.switchSymbol) {
            const originalSwitch = window.dashboard.switchSymbol;
            window.dashboard.switchSymbol = (symbol) => {
                originalSwitch.call(window.dashboard, symbol);
                this.currentSymbol = symbol;
                document.getElementById('eventsSymbolSelect').value = symbol;
                setTimeout(() => this.loadEvents(), 500);
            };
        }
    }

    async loadEvents() {
        const symbol = document.getElementById('eventsSymbolSelect').value;
        const lookback = document.getElementById('lookbackSelect').value;

        console.log(`📈 Loading market events for ${symbol} (${lookback} days lookback)`);

        const container = document.getElementById('marketEventsResults');
        container.innerHTML = `
        <div class="text-center py-4">
            <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
            <p class="mt-2 text-white">Analyzing past market events...</p>
        </div>
    `;

        try {
            const response = await fetch(`/api/backtest/actual-events/${symbol}?lookbackDays=${lookback}`);
            const data = await response.json();

            console.log('✅ Market events received:', data);

            // DEBUG: Analyze event data structure
            console.log('🔍 Analyzing event data structure...');
            if (data.events && data.events.length > 0) {
                const sampleEvent = data.events[0];
                console.log('Sample event:', sampleEvent);
                console.log('Available keys:', Object.keys(sampleEvent));

                // Check if we have pivot type information
                if (sampleEvent.signalDetails) {
                    console.log('Signal details:', sampleEvent.signalDetails);
                }

                // Check source pivot info
                console.log('Source pivot?', sampleEvent.sourcePivot);

                // Count pivot types if available
                const pivotTypes = data.events.reduce((acc, event) => {
                    const type = event.pivotType || event.signalDetails?.pivotType || 'UNKNOWN';
                    acc[type] = (acc[type] || 0) + 1;
                    return acc;
                }, {});
                console.log('Pivot types distribution:', pivotTypes);
            }

            console.log('🔍 Checking for MAJOR pivots...');
            const uniquePivots = new Set();
            data.events.forEach(event => {
                const pivot = event.signalDetails?.sourcePivot;
                if (pivot) {
                    uniquePivots.add(`${pivot.date}: $${pivot.price} (${pivot.type})`);
                }
            });
            console.log('Unique source pivots found:', Array.from(uniquePivots));

            this.eventsData = data; // Store the data
            this.renderEvents(data);

        } catch (error) {
            console.error('❌ Failed to load market events:', error);
            container.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-triangle me-2"></i>
                Failed to load market events: ${error.message}
            </div>
        `;
        }
    }

    extractGannPeriod(periodString) {
        if (!periodString) return null;
        // Extract numeric period from strings like "90D_ANNIVERSARY" or "90D"
        const match = periodString.match(/(\d+)/);
        return match ? parseInt(match[1]) : null;
    }

    analyzeGannCycles(events) {
        const cycleAnalysis = {};

        // Initialize for all known cycles
        this.gannCycles.forEach(cycle => {
            cycleAnalysis[cycle] = {
                cycle: cycle,
                count: 0,
                upCount: 0,
                downCount: 0,
                totalUp: 0,
                totalDown: 0,
                maxUp: 0,
                maxDown: 0,
                events: []
            };
        });

        // Analyze each event
        events.forEach(event => {
            if (event.signalType === 'GANN' && event.signalDetails?.period) {
                const period = this.extractGannPeriod(event.signalDetails.period);
                if (period && cycleAnalysis[period]) {
                    const analysis = cycleAnalysis[period];
                    analysis.count++;
                    analysis.events.push(event);

                    const change = event.dailyChangePercent || 0;
                    if (event.direction === 'UP') {
                        analysis.upCount++;
                        analysis.totalUp += Math.abs(change);
                        analysis.maxUp = Math.max(analysis.maxUp, Math.abs(change));
                    } else if (event.direction === 'DOWN') {
                        analysis.downCount++;
                        analysis.totalDown += Math.abs(change);
                        analysis.maxDown = Math.max(analysis.maxDown, Math.abs(change));
                    }
                }
            }
        });

        // Calculate derived metrics
        Object.values(cycleAnalysis).forEach(analysis => {
            if (analysis.count > 0) {
                analysis.successRate = (analysis.upCount / analysis.count) * 100;
                analysis.avgMove = ((analysis.totalUp + analysis.totalDown) / analysis.count);
                analysis.netMove = analysis.totalUp - analysis.totalDown;
                analysis.avgUp = analysis.upCount > 0 ? analysis.totalUp / analysis.upCount : 0;
                analysis.avgDown = analysis.downCount > 0 ? analysis.totalDown / analysis.downCount : 0;
            } else {
                analysis.successRate = 0;
                analysis.avgMove = 0;
                analysis.netMove = 0;
                analysis.avgUp = 0;
                analysis.avgDown = 0;
            }
        });

        return cycleAnalysis;
    }

    calculateMoveTotals(events) {
        let totalUp = 0;
        let totalDown = 0;
        let upCount = 0;
        let downCount = 0;
        let maxUp = 0;
        let maxDown = 0;

        events.forEach(event => {
            const change = event.dailyChangePercent || 0;
            if (event.direction === 'UP') {
                totalUp += Math.abs(change);
                upCount++;
                maxUp = Math.max(maxUp, Math.abs(change));
            } else if (event.direction === 'DOWN') {
                totalDown += Math.abs(change);
                downCount++;
                maxDown = Math.max(maxDown, Math.abs(change));
            }
        });

        const totalEvents = upCount + downCount;
        const netMove = totalUp - totalDown;
        const avgUp = upCount > 0 ? totalUp / upCount : 0;
        const avgDown = downCount > 0 ? totalDown / downCount : 0;

        return {
            totalUp,
            totalDown,
            upCount,
            downCount,
            netMove,
            avgUp,
            avgDown,
            maxUp,
            maxDown,
            totalEvents
        };
    }

    renderEvents(data) {
        const container = document.getElementById('marketEventsResults');
        const events = data.events || [];

        if (events.length === 0) {
            container.innerHTML = `
                <div class="alert alert-info">
                    <i class="fas fa-info-circle me-2"></i>
                    <strong>No past events found</strong>
                    <div class="mt-2 small">
                        Try increasing the lookback period or check if ${data.symbol} has enough historical data.
                    </div>
                </div>
            `;
            return;
        }

        // Filter events based on current filter
        let filteredEvents = this.filterSignificantEvents(events);

        // Apply direction filter
        if (this.currentFilter === 'UP') {
            filteredEvents = filteredEvents.filter(e => e.direction === 'UP');
        } else if (this.currentFilter === 'DOWN') {
            filteredEvents = filteredEvents.filter(e => e.direction === 'DOWN');
        }

        // Calculate overall move totals
        const moveTotals = this.calculateMoveTotals(filteredEvents);

        // Analyze Gann cycles
        const gannAnalysis = this.analyzeGannCycles(filteredEvents);
        const activeCycles = Object.values(gannAnalysis).filter(c => c.count > 0);

        // Count event types for summary
        const fibEvents = filteredEvents.filter(e => e.signalType === 'FIBONACCI').length;
        const gannEvents = filteredEvents.filter(e => e.signalType === 'GANN').length;

        // Calculate percentages
        const upPercentage = moveTotals.upCount > 0 ? (moveTotals.upCount / moveTotals.totalEvents * 100).toFixed(1) : '0.0';
        const downPercentage = moveTotals.downCount > 0 ? (moveTotals.downCount / moveTotals.totalEvents * 100).toFixed(1) : '0.0';

        // Calculate visual indicator position
        const totalMagnitude = moveTotals.totalUp + moveTotals.totalDown;
        const upPosition = totalMagnitude > 0 ? (moveTotals.totalUp / totalMagnitude * 100).toFixed(1) : '50';

        container.innerHTML = `
            <div class="mb-3">
                <!-- Event Counts -->
                <div class="d-flex flex-wrap align-items-center mb-2">
                    <span class="badge bg-primary me-2">${filteredEvents.length} events</span>
                    <span class="badge bg-indigo me-2">${fibEvents} Fibonacci</span>
                    <span class="badge bg-teal me-2">${gannEvents} Gann</span>
                    <span class="badge bg-success me-2">${moveTotals.upCount} ↑ (${upPercentage}%)</span>
                    <span class="badge bg-danger me-2">${moveTotals.downCount} ↓ (${downPercentage}%)</span>
                    <span class="badge bg-dark me-2">${data.symbol}</span>
                    <button class="btn btn-sm btn-outline-info ms-auto" onclick="window.marketEvents.loadEvents()">
                        <i class="fas fa-redo me-1"></i>Refresh
                    </button>
                </div>
                
                <!-- Overall Move Totals -->
                <div class="move-summary bg-dark p-3 rounded mb-3">
                    <h6 class="text-white mb-3">📊 Overall Cumulative Move Analysis</h6>
                    <div class="row">
                        <div class="col-md-6">
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Total Up Magnitude:</span>
                                <span class="total-up">+${moveTotals.totalUp.toFixed(2)}%</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Total Down Magnitude:</span>
                                <span class="total-down">-${moveTotals.totalDown.toFixed(2)}%</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Net Move:</span>
                                <span class="${moveTotals.netMove >= 0 ? 'total-up' : 'total-down'} fw-bold">
                                    ${moveTotals.netMove >= 0 ? '+' : ''}${moveTotals.netMove.toFixed(2)}%
                                </span>
                            </div>
                        </div>
                        <div class="col-md-6">
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Avg Up Move:</span>
                                <span class="total-up">+${moveTotals.avgUp.toFixed(2)}%</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Avg Down Move:</span>
                                <span class="total-down">-${moveTotals.avgDown.toFixed(2)}%</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-white">Max Single Move:</span>
                                <span class="${moveTotals.maxUp >= moveTotals.maxDown ? 'total-up' : 'total-down'}">
                                    ${moveTotals.maxUp >= moveTotals.maxDown ? '+' : '-'}${Math.max(moveTotals.maxUp, moveTotals.maxDown).toFixed(2)}%
                                </span>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Visual Indicator -->
                    <div class="mt-3">
                        <div class="d-flex justify-content-between mb-1">
                            <small class="text-white">Up/Down Balance:</small>
                            <small class="text-white">${upPosition}% up / ${(100 - upPosition).toFixed(1)}% down</small>
                        </div>
                        <div style="position: relative; height: 8px; background: #374151; border-radius: 4px; overflow: hidden;">
                            <div style="position: absolute; left: 0; width: ${upPosition}%; height: 100%; background: linear-gradient(90deg, #065f46, #10b981);"></div>
                            <div style="position: absolute; left: ${upPosition}%; width: ${100 - upPosition}%; height: 100%; background: linear-gradient(90deg, #991b1b, #ef4444);"></div>
                        </div>
                    </div>
                </div>
                
<!-- Gann Cycle Analysis -->
${activeCycles.length > 0 ? `
<div class="move-summary bg-dark p-3 rounded mb-3">
    <h6 class="text-white mb-3">📈 Gann Cycle Performance Analysis</h6>
    <p class="text-white small mb-3">Analyzing ${activeCycles.length} active Gann cycles</p>
    
    <div class="table-responsive">
    <table class="table table-sm table-borderless text-white mb-0">
        <thead>
            <tr>
                <th class="ps-0">Cycle</th>
                <th class="text-center">Events</th>
                <th class="text-center">Net Move</th>
                <th class="text-center">Avg Move</th>
                <th class="text-center">Max Move</th>
                <th class="pe-0">Up/Down</th>
            </tr>
        </thead>
        <tbody>
            ${activeCycles
            .sort((a, b) => b.netMove - a.netMove)
            .map(cycle => this.renderCycleRow(cycle))
            .join('')
        }
        </tbody>
    </table>
</div>
    
    <!-- Top Performing Cycles -->
    ${activeCycles.length >= 3 ? `
    <div class="mt-3">
        <h6 class="text-white mb-2">🎯 Top Performing Cycles (by Net Move)</h6>
        <div class="d-flex flex-wrap gap-2">
            ${activeCycles
            .sort((a, b) => b.netMove - a.netMove)
            .slice(0, 3)
            .map(cycle => {
                const netMoveClass = cycle.netMove >= 0 ? 'total-up' : 'total-down';
                const avgMoveClass = cycle.avgMove >= 0 ? 'total-up' : 'total-down';

                return `
                    <div class="bg-purple p-2 rounded">
                    <div class="fw-bold text-white">${cycle.cycle}D</div>
                    <div class="small text-white">
                    Net: <span class="text-white">${cycle.netMove >= 0 ? '+' : ''}${cycle.netMove.toFixed(2)}%</span>
                </div>
            <div class="small text-white">
            Avg: <span class="text-white">${cycle.avgMove >= 0 ? '+' : ''}${cycle.avgMove.toFixed(2)}%</span>
        </div>
        <div class="very-small text-white-50">${cycle.count} events</div>
    </div>
`;
            }).join('')
        }
        </div>
    </div>
    ` : ''}
</div>
` : ''}

            
            <!-- Events Table -->
            <div class="table-responsive">
                <table class="table table-dark table-sm table-hover">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Signal Type</th>
                            <th>Signal Details</th>
                            <th>Price</th>
                            <th>Daily Change</th>
                            <th>Direction</th>
                            <th>3-Day Change</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${filteredEvents.map(event => this.renderEventRow(event)).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    renderCycleRow(cycle) {
        const netMoveClass = cycle.netMove >= 0 ? 'total-up' : 'total-down';
        const avgMoveClass = cycle.avgMove >= 0 ? 'total-up' : 'total-down';
        const maxMove = Math.max(cycle.maxUp, cycle.maxDown);
        const maxMoveClass = cycle.maxUp >= cycle.maxDown ? 'total-up' : 'total-down';

        return `
        <tr class="cycle-row">
            <td class="ps-0">
                <span class="badge bg-purple cycle-badge">${cycle.cycle}D</span>
            </td>
            <td class="text-center">${cycle.count}</td>
            <td class="text-center ${netMoveClass} fw-bold">
                ${cycle.netMove >= 0 ? '+' : ''}${cycle.netMove.toFixed(2)}%
                <div class="very-small">Net</div>
            </td>
            <td class="text-center ${avgMoveClass}">
                ${cycle.avgMove >= 0 ? '+' : ''}${cycle.avgMove.toFixed(2)}%
                <div class="very-small">Avg</div>
            </td>
            <td class="text-center ${maxMoveClass}">
                ${cycle.maxUp >= cycle.maxDown ? '+' : '-'}${maxMove.toFixed(2)}%
                <div class="very-small">Max</div>
            </td>
            <td class="pe-0">
                <div class="d-flex flex-column align-items-center">
                    <div class="d-flex">
                        <small class="me-2 text-success">↑${cycle.upCount}</small>
                        <small class="text-danger">↓${cycle.downCount}</small>
                    </div>
                    <div class="very-small mt-1">${cycle.upCount}/${cycle.downCount}</div>
                </div>
            </td>
        </tr>
    `;
    }

    filterSignificantEvents(events) {
        // Focus on these 3 key recent MAJOR pivots
        const majorPivots = [
            {date: '2023-01-01', price: 15455.0, label: '2023 Bear Bottom'},
            // {date: '2024-03-01', price: 72000.0, label: '2024 Cycle High'},
            {date: '2025-10-01', price: 126272.76, label: '2025 Projected High'}
        ];

        // Find events that come from these MAJOR pivots
        const majorEvents = events.filter(event => {
            const sourcePivot = event.signalDetails?.sourcePivot;
            if (!sourcePivot) return false;

            // Try to match by date first (most accurate)
            const sourceDate = sourcePivot.date;
            const majorPivot = majorPivots.find(p => p.date === sourceDate);

            if (majorPivot) {
                console.log(`✅ MAJOR pivot match by date: ${sourceDate} $${sourcePivot.price} (${majorPivot.label})`);
                return true;
            }

            // If no date match, try to match by price (within 5%)
            const sourcePrice = sourcePivot.price;
            const priceMatch = majorPivots.find(p => {
                const deviation = Math.abs(sourcePrice - p.price) / p.price;
                return deviation < 0.05; // Within 5%
            });

            if (priceMatch) {
                console.log(`✅ MAJOR pivot match by price: ${sourceDate} $${sourcePrice} ≈ $${priceMatch.price} (${priceMatch.label})`);
                return true;
            }

            return false;
        });

        console.log(`📊 MAJOR pivot filtering:`, {
            totalEvents: events.length,
            majorEvents: majorEvents.length,
            filteredOut: events.length - majorEvents.length,
            percentage: `${((majorEvents.length / events.length) * 100).toFixed(1)}% from key pivots`
        });

        // If we found events from our key pivots, use them
        if (majorEvents.length > 0) {
            // Log which pivots were actually used
            const pivotUsage = {};
            majorEvents.forEach(event => {
                const pivot = event.signalDetails.sourcePivot;
                const key = `${pivot.date}: $${pivot.price}`;
                pivotUsage[key] = (pivotUsage[key] || 0) + 1;
            });
            console.log('Key pivot usage:', pivotUsage);

            return this.removeDuplicates(majorEvents);
        }

        // If no key pivot events, show a subset (e.g., last 30 events) with warning
        console.warn('⚠️ No events from key pivots found. Showing recent subset.');

        // Take most recent events and remove duplicates
        const recentEvents = events
            .sort((a, b) => new Date(b.date) - new Date(a.date))
            .slice(0, 30);

        return this.removeDuplicates(recentEvents);
    }

    removeDuplicates(events) {
        const uniqueEvents = [];
        const seenKeys = new Set();

        for (const event of events) {
            const date = event.date;
            const type = event.signalType || 'UNKNOWN';
            const key = `${date}_${type}`;

            if (!seenKeys.has(key)) {
                seenKeys.add(key);
                uniqueEvents.push(event);
            }
        }

        // Sort by date (most recent first)
        uniqueEvents.sort((a, b) => new Date(b.date) - new Date(a.date));

        return uniqueEvents;
    }

// Also update the isPredefinedMajorPivot helper:
    isPredefinedMajorPivot(pivot) {
        if (!pivot) return false;

        const majorPivots = [
            {date: '2023-01-01', price: 15455.0},
            // {date: '2024-03-01', price: 72000.0},
            {date: '2025-10-01', price: 126272.76}
        ];

        // Try exact date match first
        const dateMatch = majorPivots.find(mp => mp.date === pivot.date);
        if (dateMatch) {
            console.log(`✅ Exact date match: ${pivot.date} $${pivot.price} = KEY pivot`);
            return true;
        }

        // Try price match within 5%
        const priceMatch = majorPivots.find(mp => {
            const deviation = Math.abs(pivot.price - mp.price) / mp.price;
            const isMatch = deviation < 0.05;
            if (isMatch) {
                console.log(`✅ Price match: ${pivot.date} $${pivot.price} ≈ $${mp.price} (${(deviation * 100).toFixed(1)}% diff)`);
            }
            return isMatch;
        });

        return !!priceMatch;
    }

    renderEventRow(event) {
        const date = this.formatDate(event.date);
        const signalType = event.signalType || 'Unknown';
        const price = event.price ? `$${this.formatPrice(event.price)}` : 'N/A';
        const dailyChange = event.dailyChangePercent || 0;
        const threeDayChange = event.threeDayChangePercent || 0;
        const direction = event.direction || 'FLAT';

        // Check if this is from a MAJOR pivot
        const sourcePivot = event.signalDetails?.sourcePivot;
        const isMajorPivot = this.isPredefinedMajorPivot(sourcePivot);

        // Add MAJOR badge if from predefined pivot
        const majorBadge = isMajorPivot ?
            '<span class="badge bg-gold ms-1" title="From predefined MAJOR pivot">MAJOR</span>' : '';

        // Get signal details
        let signalDetails = '';
        let signalBadge = '';

        if (event.signalDetails) {
            if (signalType === 'GANN') {
                const period = event.signalDetails.period || 'Unknown';
                const cleanPeriod = period.replace('D_ANNIVERSARY', 'D');
                signalDetails = `Gann ${cleanPeriod} Cycle`;
                signalBadge = `<span class="badge bg-purple">${cleanPeriod}</span>`;

                // Add source pivot info for MAJOR pivots
                if (isMajorPivot && sourcePivot) {
                    signalDetails += `<div class="very-small text-white-50 mt-1">
                    From: ${this.formatDate(sourcePivot.date)} $${this.formatPrice(sourcePivot.price)}
                </div>`;
                }
            } else if (signalType === 'FIBONACCI') {
                const ratio = event.signalDetails.ratio || 0;
                const days = event.signalDetails.days || 0;
                signalDetails = `Fib ${ratio.toFixed(3)} (${days} days)`;
                signalBadge = `<span class="badge bg-indigo">Fib ${ratio.toFixed(3)}</span>`;
            }
        }

        // Determine direction display
        let directionDisplay = '';
        let directionClass = '';

        if (direction === 'UP') {
            directionDisplay = '<i class="fas fa-arrow-up text-success"></i> Up';
            directionClass = 'text-success fw-bold';
        } else if (direction === 'DOWN') {
            directionDisplay = '<i class="fas fa-arrow-down text-danger"></i> Down';
            directionClass = 'text-danger fw-bold';
        } else {
            directionDisplay = '<i class="fas fa-minus text-white"></i> Flat';
            directionClass = 'text-white fw-bold';
        }

        // Add row class for MAJOR pivots
        const rowClass = isMajorPivot ? 'table-warning' : '';

        return `
        <tr class="${rowClass}">
            <td class="fw-bold">
                ${date}
                ${majorBadge}
            </td>
            <td>
                <span class="badge ${signalType === 'GANN' ? 'bg-purple' : 'bg-indigo'}">
                    ${signalType}
                </span>
            </td>
            <td>
                <div class="small">${signalDetails}</div>
                <div class="mt-1">${signalBadge}</div>
            </td>
            <td class="fw-bold text-white">${price}</td>
            <td class="${dailyChange > 0 ? 'text-success' : dailyChange < 0 ? 'text-danger' : 'text-white'} fw-bold">
                ${dailyChange > 0 ? '+' : ''}${dailyChange.toFixed(2)}%
            </td>
            <td class="${directionClass}">
                ${directionDisplay}
            </td>
            <td class="${threeDayChange > 0 ? 'text-success' : threeDayChange < 0 ? 'text-danger' : 'text-white'}">
                ${threeDayChange > 0 ? '+' : ''}${threeDayChange.toFixed(2)}%
            </td>
        </tr>
    `;
    }

    formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
                year: 'numeric'
            });
        } catch (e) {
            console.error('Date parsing error:', e, 'for date:', dateString);
            return dateString;
        }
    }

    formatPrice(price) {
        if (!price) return 'N/A';
        return price.toLocaleString(undefined, {
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        });
    }
}

// ===== FIBONACCI HIT TESTER CLASS =====
class FibonacciHitTester {
    constructor() {
        this.currentSymbol = 'BTC';
        this.isLoading = false;
        this.initialize();
    }

    initialize() {
        console.log('🎯 Initializing Fibonacci Hit Tester...');

        // Set up event listeners
        document.getElementById('runFibHitTestBtn')?.addEventListener('click', () => this.runTest());

        // Listen for symbol changes from main dashboard
        this.listenForSymbolChanges();

        // Auto-run after page loads
        setTimeout(() => {
            this.runTest();
        }, 1500);
    }

    listenForSymbolChanges() {
        if (window.dashboard && window.dashboard.switchSymbol) {
            const originalSwitch = window.dashboard.switchSymbol;
            window.dashboard.switchSymbol = (symbol) => {
                originalSwitch.call(window.dashboard, symbol);
                this.currentSymbol = symbol;
                setTimeout(() => this.runTest(), 500); // Run test after symbol change
            };
        }
    }

    async runTest() {
        if (this.isLoading) return;

        const symbol = this.currentSymbol;
        const margin = document.getElementById('marginSelect')?.value || '2.0';
        const tolerance = document.getElementById('toleranceSelect')?.value || '3';

        console.log(`🎯 Running Fibonacci hit test for ${symbol}, margin: ${margin}%, tolerance: ±${tolerance} days`);

        this.isLoading = true;
        this.showLoading();

        try {
            const response = await fetch(`/api/backtest/fibonacci-hits/${symbol}?margin=${margin}&tolerance=${tolerance}`);

            if (!response.ok) {
                throw new Error(`API error: ${response.status}`);
            }

            const data = await response.json();
            console.log('✅ Fibonacci hit data received:', data);

            this.renderResults(data);

        } catch (error) {
            console.error('❌ Failed to run Fibonacci hit test:', error);
            this.showError(error);
        } finally {
            this.isLoading = false;
        }
    }

    showLoading() {
        const resultsDiv = document.getElementById('fibHitResults');
        if (!resultsDiv) return;

        resultsDiv.innerHTML = `
            <div class="text-center py-4">
                <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
                <p class="mt-2 text-white">Analyzing historical Fibonacci projections...</p>
                <p class="small text-white">Checking for significant price moves around Fibonacci dates</p>
            </div>
        `;

        // Clear summary
        const summaryDiv = document.getElementById('hitSummary');
        if (summaryDiv) summaryDiv.innerHTML = '';
    }

    showError(error) {
        const resultsDiv = document.getElementById('fibHitResults');
        if (!resultsDiv) return;

        resultsDiv.innerHTML = `
            <div class="alert alert-danger">
                <h5><i class="fas fa-exclamation-triangle me-2"></i>Analysis Failed</h5>
                <p class="mb-1">${error.message || 'Unknown error'}</p>
                <small>Check API endpoint and try again</small>
                <div class="mt-2">
                    <button class="btn btn-sm btn-outline-warning" onclick="window.fibHitTester.runTest()">
                        <i class="fas fa-redo me-1"></i>Retry
                    </button>
                </div>
            </div>
        `;
    }

    renderResults(data) {
        const resultsDiv = document.getElementById('fibHitResults');
        const summaryDiv = document.getElementById('hitSummary');

        if (!resultsDiv || !summaryDiv) return;

        // Update summary
        const hitRate = data.hitRate || 0;
        summaryDiv.innerHTML = `
            <span class="badge ${hitRate >= 60 ? 'bg-success' : hitRate >= 40 ? 'bg-warning' : 'bg-danger'}">
                ${hitRate.toFixed(1)}% Hit Rate
            </span>
            <div class="small text-white">
                ${data.successfulHits || 0}/${data.totalTests || 0} hits
            </div>
        `;

        // Check if we have hits
        if (!data.hits || data.hits.length === 0) {
            resultsDiv.innerHTML = `
                <div class="alert alert-info">
                    <i class="fas fa-info-circle me-2"></i>
                    <strong>No significant Fibonacci hits found</strong>
                    <div class="mt-2 small">
                        Tried ${data.totalTests || 0} historical projections with ±${data.toleranceDays || 3} day tolerance.
                        <div class="mt-1">Try adjusting the move threshold or date tolerance.</div>
                    </div>
                </div>
            `;
            return;
        }

        // Show results table (most recent first)
        const recentHits = [...data.hits].slice(-15).reverse();

        resultsDiv.innerHTML = `
            <div class="table-responsive">
                <table class="table table-dark table-sm table-hover">
                    <thead>
                        <tr>
                            <th>Projection Date</th>
                            <th>Fib Ratio</th>
                            <th>From Pivot</th>
                            <th>Actual Move</th>
                            <th>Hit/Miss</th>
                            <th>Days Off</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${recentHits.map(hit => this.renderHitRow(hit)).join('')}
                    </tbody>
                </table>
            </div>
            
            ${data.hits.length > 15 ? `
                <div class="text-center mt-3">
                    <small class="text-white">
                        Showing 15 most recent of ${data.hits.length} total hits
                        <button class="btn btn-sm btn-outline-info ms-2" onclick="window.fibHitTester.showAllHits(${JSON.stringify(data.hits).replace(/'/g, "\\'")})">
                            <i class="fas fa-list me-1"></i>Show All
                        </button>
                    </small>
                </div>
            ` : ''}
        `;
    }

    renderHitRow(hit) {
        // Safely handle potentially missing data
        const pivotDate = hit.pivotDate ? this.formatDate(hit.pivotDate) : 'N/A';
        const pivotPrice = hit.pivotPrice ? `$${hit.pivotPrice.toFixed(0)}` : 'N/A';
        const projectedDate = hit.projectedDate ? this.formatDate(hit.projectedDate) : 'N/A';
        const actualMoveDate = hit.actualMoveDate ? this.formatDate(hit.actualMoveDate) : 'N/A';
        const movePercent = hit.movePercent ? hit.movePercent.toFixed(2) : '0.00';
        const direction = hit.direction || 'NONE';
        const fibRatio = hit.fibonacciRatio ? hit.fibonacciRatio.toFixed(3) : 'N/A';
        const isHit = hit.hit === true;
        const daysOff = hit.daysFromProjection || 0;

        // Determine Fibonacci ratio type for badge color
        const ratioType = this.getRatioType(hit.fibonacciRatio);
        const ratioBadgeClass = ratioType === 'Harmonic' ? 'bg-purple' :
            ratioType === 'Geometric' ? 'bg-orange' :
                ratioType === 'Double' ? 'bg-teal' :
                    ratioType === 'Triple' ? 'bg-pink' : 'bg-secondary';

        return `
            <tr>
                <td>
                    <div class="small">${projectedDate}</div>
                    ${actualMoveDate !== projectedDate ?
            `<div class="very-small text-white-50">actual: ${actualMoveDate}</div>` : ''}
                </td>
                <td>
                    <span class="badge ${ratioBadgeClass}">
                        ${ratioType} ${fibRatio}
                    </span>
                </td>
                <td>
                    <div class="small">
                        ${pivotDate}
                        <div class="very-small text-white-50">
                            ${pivotPrice} (${hit.pivotType || 'PIVOT'})
                        </div>
                    </div>
                </td>
                <td class="${direction === 'UP' ? 'text-success' : direction === 'DOWN' ? 'text-danger' : 'text-white'}">
                    <strong>${direction === 'UP' ? '↑' : direction === 'DOWN' ? '↓' : '•'} ${Math.abs(movePercent)}%</strong>
                </td>
                <td>
                    <span class="badge ${isHit ? 'bg-success' : 'bg-secondary'}">
                        ${isHit ? '✓ HIT' : 'Miss'}
                    </span>
                </td>
                <td>
                    <span class="small ${Math.abs(daysOff) <= 1 ? 'text-success' : Math.abs(daysOff) <= 3 ? 'text-warning' : 'text-danger'}">
                        ${daysOff >= 0 ? '+' : ''}${daysOff}d
                    </span>
                </td>
            </tr>
        `;
    }

    getRatioType(ratio) {
        if (!ratio) return 'Fib';

        // Use tolerance for comparison
        const isApprox = (a, b) => Math.abs(a - b) < 0.001;

        if (isApprox(ratio, 0.333) || isApprox(ratio, 0.667) ||
            isApprox(ratio, 1.333) || isApprox(ratio, 1.667) ||
            isApprox(ratio, 2.333) || isApprox(ratio, 2.667)) {
            return 'Harmonic';
        }
        if (isApprox(ratio, 1.5) || isApprox(ratio, 2.5) || isApprox(ratio, 3.5)) {
            return 'Geometric';
        }
        if (isApprox(ratio, 2.0)) return 'Double';
        if (isApprox(ratio, 3.0)) return 'Triple';
        return 'Fib';
    }

    formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
                year: date.getFullYear() !== new Date().getFullYear() ? '2-digit' : undefined
            });
        } catch (e) {
            return dateString;
        }
    }

    showAllHits(allHits) {
        // This would show a modal or expand the table
        console.log('Showing all hits:', allHits.length);
        alert(`Total hits found: ${allHits.length}\n\nView browser console for full data.`);
    }
}

// ===== PAGE INITIALIZATION =====
window.addEventListener('load', function() {
    console.log('📱 Initializing Fibonacci Time Trader Dashboard...');

    // Initialize dashboard
    window.dashboard = new TimeGeometryDashboard();

    // Initialize market events display
    window.marketEvents = new MarketEventsDisplay();

    // Initialize Fibonacci hit tester
    window.fibHitTester = new FibonacciHitTester();

    // Load initial data
    setTimeout(() => {
        try {
            window.dashboard.loadAllData('BTC');
            window.dashboard.updateTimestamp();
        } catch (error) {
            console.error('Failed to load initial data:', error);
        }
    }, 100);

    // Load backtest data
    setTimeout(() => {
        loadAndDisplayBacktestData();
    }, 500);

    // Add CSS for better styling
    const style = document.createElement('style');
    style.textContent = `
        .very-small {
            font-size: 0.7rem;
        }
        .bg-purple {
            background-color: #7c3aed !important;
        }
        .bg-orange {
            background-color: #f59e0b !important;
        }
        .bg-teal {
            background-color: #14b8a6 !important;
        }
        .bg-pink {
            background-color: #ec4899 !important;
        }
        .table-hover tbody tr:hover {
            background-color: rgba(124, 58, 237, 0.1) !important;
        }
    `;
    document.head.appendChild(style);
});

// Make sure backtest functions work with dashboard symbol changes
if (window.dashboard && window.dashboard.switchSymbol) {
    const originalSwitch = window.dashboard.switchSymbol;
    window.dashboard.switchSymbol = function(symbol) {
        originalSwitch.call(this, symbol);
        console.log(`🔄 Backtest updating to: ${symbol}`);
        // You could add logic to reload backtest for new symbol here
    };
}

console.log('✅ App.js fully loaded and ready');