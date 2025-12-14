/**
 * Fibonacci Time Trader - Pure Time Geometry Analysis
 */

class TimeGeometryDashboard {

    constructor() {
        this.currentSymbol = 'BTC';
        this.charts = new Map();
        this.solarData = null;
        this.timeGeometryData = null;
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
        document.getElementById('lastUpdated').textContent = this.lastUpdated.toLocaleTimeString();

        // Load new data
        this.loadAllData(symbol);
    }

    // Load ALL data (time geometry + solar) in parallel
    async loadAllData(symbol) {
        console.log(`📊 Loading ALL data for ${symbol}...`);

        const analysisContent = document.getElementById('analysisStats');
        const upcomingSection = document.getElementById('upcomingDates');
        const projectionsSection = document.getElementById('fibonacciProjections');
        const timelineChart = document.getElementById('timelineChart');

        try {
            // Load both data sources in parallel with timeout
            const controller1 = new AbortController();
            const controller2 = new AbortController();
            const timeoutId = setTimeout(() => {
                controller1.abort();
                controller2.abort();
            }, 15000);

            const [timeGeometryResponse, solarResponse] = await Promise.all([
                fetch(`/api/timeGeometry/analysis/${symbol}`, {
                    signal: controller1.signal
                }),
                fetch('/api/solar/forecast', {
                    signal: controller2.signal
                })
            ]);

            clearTimeout(timeoutId);

            if (!timeGeometryResponse.ok) {
                throw new Error(`Time Geometry API Error: ${timeGeometryResponse.status}`);
            }

            if (!solarResponse.ok) {
                console.warn('Solar API failed, continuing without solar data');
                // Continue without solar data
                const analysis = await timeGeometryResponse.json();
                this.timeGeometryData = analysis;
                this.solarData = null;

                // Update UI components
                this.renderUpcomingDates(analysis.vortexWindows || []);
                this.renderFibonacciProjections(analysis.fibonacciTimeProjections || []);
                this.renderSolarDashboard({ forecast: [], currentAp: 0, issueDate: 'Unavailable' });
                this.createTimelineChart(analysis);

                // Load Gann dates separately
                this.loadGannDates(symbol);
                return;
            }

            const [analysis, solarForecast] = await Promise.all([
                timeGeometryResponse.json(),
                solarResponse.json()
            ]);

            this.timeGeometryData = analysis;
            this.solarData = solarForecast;

            console.log('✅ ALL data loaded');

            // Update all UI components
            this.renderUpcomingDates(analysis.vortexWindows || []);
            this.renderFibonacciProjections(analysis.fibonacciTimeProjections || []);
            this.renderSolarDashboard(solarForecast);
            this.createTimelineChart(analysis);

            // Load Gann dates separately (has its own API endpoint)
            this.loadGannDates(symbol);

        } catch (error) {
            console.error('❌ Failed to load ALL data:', error);

            const errorMessage = error.name === 'AbortError' ?
                'Request timeout: API took too long to respond' :
                error.message;

            if (analysisContent) {
                analysisContent.innerHTML = `
                    <div class="alert alert-danger">
                        <h6><i class="fas fa-exclamation-triangle"></i> Data Error</h6>
                        <p class="mb-1">${errorMessage}</p>
                        <small>Check API endpoints</small>
                    </div>
                `;
            }
        }
    }

    // Load Gann dates from dedicated backend API
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

    // Render Gann dates list from API response
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

        // Sort by date and take first 10
        const futureDates = gannDates
            .filter(gann => {
                const gannDate = new Date(gann.date);
                return gannDate > new Date();
            })
            .sort((a, b) => new Date(a.date) - new Date(b.date))
            .slice(0, 10);

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

            return `
                <div class="date-card gann-date-card">
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
                                $${sourcePivot.price ? sourcePivot.price.toFixed(2) : 'N/A'}
                            </span>
                        </div>
                        
                        <div class="description small text-muted">
                            <div class="mb-1">
                                <i class="fas fa-arrow-right me-1"></i>
                                From ${this.formatDate(sourcePivot.date)}
                            </div>
                        </div>
                    ` : `
                        <div class="description text-muted">
                            Gann ${period.toLowerCase()} anniversary
                        </div>
                    `}
                </div>
            `;
        }).join('');
    }

    // Render solar dashboard
    renderSolarDashboard(forecast) {
        const container = document.getElementById('solarDashboard');
        if (!container || !forecast) return;

        const highApDays = forecast.forecast || [];
        const currentAp = forecast.currentAp || 0;
        const issueDate = forecast.issueDate || 'Unknown';

        container.innerHTML = `
            <div class="glass-card-inner">
                <div class="row mb-4">
                    <div class="col-md-4">
                        <div class="card ${currentAp >= 12 ? 'border-warning' : 'border-success'}">
                            <div class="card-body text-center">
                                <div class="display-4 fw-bold">${currentAp}</div>
                                <div class="text-uppercase small">Current AP Index</div>
                                <div class="mt-2">
                                    <span class="badge ${currentAp >= 12 ? 'bg-warning' : 'bg-success'}">
                                        ${currentAp >= 12 ? 'Elevated' : 'Normal'}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <div class="col-md-4">
                        <div class="card border-info">
                            <div class="card-body text-center">
                                <div class="display-4 fw-bold">${highApDays.length}</div>
                                <div class="text-uppercase small">High AP Days (≥12)</div>
                                <div class="mt-2">
                                    <span class="badge ${highApDays.length > 0 ? 'bg-warning' : 'bg-success'}">
                                        ${highApDays.length > 0 ? 'Active Period' : 'Quiet'}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <div class="col-md-4">
                        <div class="card bg-dark text-white">
                            <div class="card-body text-center">
                                <div class="h2 mb-2">
                                    <i class="fas fa-satellite"></i>
                                </div>
                                <div class="text-uppercase small">Forecast Source</div>
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
                            Next high AP day: ${this.formatDate(highApDays[0]?.date)}
                        </div>
                    </div>
                ` : `
                    <div class="alert alert-success">
                        <i class="fas fa-check-circle me-2"></i>
                        <strong>Quiet geomagnetic period</strong>
                        <div class="mt-1 small">No days with AP ≥ 12 in the forecast</div>
                    </div>
                `}
                
                <!-- High AP Days List -->
                ${highApDays.length > 0 ? `
                    <div class="mt-4">
                        <h5><i class="fas fa-list me-2"></i>High AP Dates (AP ≥ 12)</h5>
                        <div class="table-responsive">
                            <table class="table table-sm">
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
                        ${highApDays.length > 10 ? `<div class="text-center small text-muted mt-2">+ ${highApDays.length - 10} more days</div>` : ''}
                    </div>
                ` : ''}
            </div>
        `;
    }

    // Render upcoming alignment dates (grouped by date)
    renderUpcomingDates(alignmentDates) {
        const container = document.getElementById('upcomingDates');
        if (!container) return;

        if (!alignmentDates || alignmentDates.length === 0) {
            container.innerHTML = '<div class="alert alert-info">No Fibonacci/Gann alignment dates detected</div>';
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

        container.innerHTML = futureDates.map(group => {
            const date = new Date(group.date);
            const hasMultiple = group.alignments.length > 1;

            // Get all unique signals from all alignments on this date
            const allSignals = [];
            group.alignments.forEach(alignment => {
                const factors = alignment.contributingFactors || [];
                const signals = this.parseConvergingSignals(factors);
                allSignals.push(...signals);
            });

            // Remove duplicates
            const uniqueSignals = Array.from(new Set(allSignals.map(s => s.label)))
                .map(label => allSignals.find(s => s.label === label));

            return `
                <div class="date-card ${group.maxIntensity > 0.8 ? 'vortex-highlight' : ''}">
                    <div class="date">
                        ${this.formatDate(group.date)}
                        <span class="float-end">
                            <span class="badge ${group.maxIntensity > 0.8 ? 'bg-danger' : group.maxIntensity > 0.5 ? 'bg-warning' : 'bg-primary'}">
                                ${(group.maxIntensity * 100).toFixed(0)}%
                            </span>
                        </span>
                    </div>
                    
                    <!-- Main signals display -->
                    <div class="converging-signals mb-2">
                        <div class="d-flex align-items-center mb-1">
                            <small class="text-muted me-2">Signals:</small>
                            ${uniqueSignals.slice(0, 3).map(signal =>
                `<span class="badge ${signal.type === 'fibonacci' ? 'bg-primary' : 'bg-success'} me-1">${signal.label}</span>`
            ).join('')}
                            ${uniqueSignals.length > 3 ? `<span class="badge bg-secondary">+${uniqueSignals.length - 3}</span>` : ''}
                        </div>
                        
                        <!-- Expandable additional alignments -->
                        ${hasMultiple ? `
                            <div class="additional-alignments">
                                <button class="btn btn-sm btn-outline-secondary btn-toggle" 
                                        onclick="this.nextElementSibling.classList.toggle('d-none'); this.querySelector('i').classList.toggle('fa-rotate-90')">
                                    <i class="fas fa-chevron-right fa-xs"></i>
                                    ${group.alignments.length - 1} additional alignment${group.alignments.length > 2 ? 's' : ''}
                                </button>
                                <div class="d-none mt-2">
                                    ${group.alignments.slice(1).map((alignment, idx) => {
                const signals = this.parseConvergingSignals(alignment.contributingFactors || []);
                return `
                                            <div class="additional-alignment small mb-2">
                                                <div class="d-flex justify-content-between">
                                                    <div>
                                                        ${signals.map(s =>
                    `<span class="badge ${s.type === 'fibonacci' ? 'bg-primary' : 'bg-success'} me-1">${s.label}</span>`
                ).join('')}
                                                    </div>
                                                    <span class="badge ${alignment.intensity > 0.8 ? 'bg-danger' : alignment.intensity > 0.5 ? 'bg-warning' : 'bg-info'}">
                                                        ${(alignment.intensity * 100).toFixed(0)}%
                                                    </span>
                                                </div>
                                                ${alignment.description ? `<div class="text-muted mt-1">${alignment.description}</div>` : ''}
                                            </div>
                                        `;
            }).join('')}
                                </div>
                            </div>
                        ` : ''}
                    </div>
                    
                    <!-- Main alignment description -->
                    <div class="description mb-2">
                        ${this.createAlignmentDescription(uniqueSignals)}
                    </div>
                </div>
            `;
        }).join('');
    }

    parseConvergingSignals(factors) {
        console.log('🔍 parseConvergingSignals called with factors:', factors);

        const signals = [];

        factors.forEach((factor, index) => {
            console.log(`  Processing factor ${index}: "${factor}"`);

            if (factor.startsWith('FIB_')) {
                const fibStr = factor.replace('FIB_', '');
                console.log(`    → Extracted: "${fibStr}"`);

                let ratio, days;

                if (fibStr.includes('.')) {
                    // Ratio format
                    ratio = parseFloat(fibStr);
                    days = Math.round(ratio * 100);
                    console.log(`    → Detected RATIO format: ${ratio} → ${days} days`);
                } else {
                    // Days format
                    days = parseInt(fibStr);
                    ratio = days / 100.0;
                    console.log(`    → Detected DAYS format: ${days} days → ${ratio} ratio`);
                }

                console.log(`    → Calling getFibRatioLabel(${ratio}, ${days})`);
                const label = this.getFibRatioLabel(ratio, days);
                console.log(`    → Result: "${label}"`);

                signals.push({
                    type: 'fibonacci',
                    label: label,
                    number: days,
                    ratio: ratio,
                    days: days,
                    rawFactor: factor  // For debugging
                });
            }
            else if (factor.includes('_ANNIVERSARY')) {
                const days = factor.replace('_ANNIVERSARY', '').replace('D', '');
                console.log(`    → Gann date: ${days} days`);

                signals.push({
                    type: 'gann',
                    label: `Gann ${days}D`,
                    days: parseInt(days)
                });
            }
            else {
                console.log(`    → Other signal type`);

                signals.push({
                    type: 'other',
                    label: factor
                });
            }
        });

        console.log('🔍 Returning signals:', signals);
        return signals;
    }

    // Create alignment description
    createAlignmentDescription(signals) {
        if (signals.length === 0) return 'No specific signals';

        const fibSignals = signals.filter(s => s.type === 'fibonacci');
        const gannSignals = signals.filter(s => s.type === 'gann');
        const otherSignals = signals.filter(s => s.type === 'other');

        const parts = [];


        if (fibSignals.length > 0) {
            // ✅ Use the label which now shows "Fib 0.618" instead of "F162"
            const fibLabels = fibSignals.map(s => s.label).join(', ');
            parts.push(`${fibSignals.length} Fibonacci: ${fibLabels}`);
        }

        if (gannSignals.length > 0) {
            const gannPeriods = gannSignals.map(s => s.label).join(', ');
            parts.push(`${gannSignals.length} Gann: ${gannPeriods}`);
        }

        if (otherSignals.length > 0) {
            parts.push(`${otherSignals.length} other signals`);
        }

        return parts.join(' • ');
    }

    // Render Fibonacci projections
    renderFibonacciProjections(projections) {
        const container = document.getElementById('fibonacciProjections');
        if (!container) return;

        if (!projections || projections.length === 0) {
            container.innerHTML = '<div class="alert alert-info">No Fibonacci projections available</div>';
            return;
        }

        const now = new Date();
        const upcomingProjections = projections
            .filter(proj => {
                const projDate = new Date(proj.date);
                return projDate > now;
            })
            .sort((a, b) => new Date(a.date) - new Date(b.date))
            .slice(0, 8);

        if (upcomingProjections.length === 0) {
            container.innerHTML = `
            <div class="alert alert-info">
                <i class="fas fa-filter me-2"></i>
                No upcoming Fibonacci projections
            </div>
        `;
            return;
        }

        container.innerHTML = upcomingProjections.map(proj => {
            // ✅ Calculate ratio from days if ratio not provided
            const days = proj.fibonacciNumber;
            const ratio = proj.fibonacciRatio || (days / 100.0);
            const label = proj.fibLabel || this.getFibRatioLabel(ratio, days);

            return `
            <div class="date-card ${proj.type === 'RESISTANCE' ? 'high' : 'low'}">
                <div class="date">
                    ${this.formatDate(proj.date)} 
                    <span class="badge bg-${proj.intensity > 0.8 ? 'danger' : proj.intensity > 0.5 ? 'warning' : 'primary'} float-end">
                        ${label}  <!-- ✅ Show "Fib 0.618" instead of "F62" -->
                    </span>
                </div>
                <div class="intensity mb-2">
                    <span class="badge bg-${proj.intensity > 0.8 ? 'danger' : proj.intensity > 0.5 ? 'warning' : 'info'}">
                        ${(proj.intensity * 100).toFixed(0)}%
                    </span>
                    <span class="badge ${proj.type === 'RESISTANCE' ? 'bg-danger' : 'bg-success'} ms-2">
                        ${proj.type}
                    </span>
                </div>
                <div class="description">
                    ${proj.description || `${label} ${proj.type.toLowerCase()} projection`}
                </div>
            </div>
        `;
        }).join('');
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

            // Cutoff date: December 1, 2025
            const cutoffDate = new Date('2025-12-01T00:00:00Z'); // Use UTC

            // Get solar data if available
            const solarHighApDays = this.solarData?.forecast || [];

            // Process solar dates CORRECTLY - PUT AT TOP (y=99)
            const solarEvents = solarHighApDays
                .filter(day => {
                    if (!day.date || day.ap < 12) return false;

                    // Parse date correctly - ensure it's treated as UTC
                    const eventDate = new Date(day.date + 'T00:00:00Z');
                    return eventDate >= cutoffDate;
                })
                .map(day => {
                    const date = new Date(day.date + 'T12:00:00Z'); // Use noon for visibility
                    return {
                        x: date,
                        y: 99,  // Top of chart
                        ap: day.ap,
                        type: 'solar',
                        event: { date: day.date, ap: day.ap }
                    };
                })
                .sort((a, b) => a.x - b.x);

            // Separate Gann dates from Fibonacci dates
            const allAlignmentDates = analysis.vortexWindows || [];
            const allFibonacciProjections = analysis.fibonacciTimeProjections || [];

            // Process alignment dates - check if they contain Gann signals
            const gannDates = [];
            const fibAlignmentDates = [];

            allAlignmentDates.forEach(alignment => {
                const signalDate = new Date(alignment.date);
                if (signalDate < cutoffDate) return;

                const factors = alignment.contributingFactors || [];
                const hasGann = factors.some(f => f.includes('_ANNIVERSARY'));
                const hasFibonacci = factors.some(f => f.startsWith('FIB_'));

                if (hasGann) {
                    gannDates.push({
                        x: signalDate,
                        y: alignment.intensity * 100,
                        type: 'gann',
                        alignment: alignment
                    });
                } else if (hasFibonacci) {
                    fibAlignmentDates.push({
                        x: signalDate,
                        y: alignment.intensity * 100,
                        type: 'fib_alignment',
                        alignment: alignment
                    });
                }
            });

            const standaloneFibonacci = allFibonacciProjections
                .filter(p => {
                    const signalDate = new Date(p.date);
                    return signalDate >= cutoffDate;
                })
                .map(p => {
                    // USE THE RATIO FROM BACKEND IF AVAILABLE
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

            // Process fib alignment dates for proper labels
            const processedFibAlignments = fibAlignmentDates.map(signal => {
                const factors = signal.alignment.contributingFactors || [];
                const fibSignals = factors.filter(f => f.startsWith('FIB_'));

                // Convert FIB_13 to Fib 0.13 (assuming 100-day base)
                const ratioLabels = fibSignals.map(f => {
                    const fibNumber = parseInt(f.replace('FIB_', ''));
                    const ratio = fibNumber / 100.0;
                    return this.getFibRatioLabel(ratio, fibNumber);
                });

                return {
                    x: signal.x,
                    y: signal.y,
                    type: 'fib_alignment',
                    alignment: signal.alignment,
                    ratioLabels: ratioLabels,
                    fibSignals: fibSignals
                };
            });

            // Combine all signals
            const allSignals = [
                ...gannDates,
                ...processedFibAlignments,
                ...standaloneFibonacci,
                ...solarEvents.map(event => ({
                    x: event.x,
                    y: event.y,
                    type: 'solar',
                    event: event
                }))
            ].sort((a, b) => a.x - b.x);

            if (allSignals.length === 0) {
                ctx.parentElement.innerHTML = `
                <div class="text-center py-4">
                    <i class="fas fa-filter fa-2x text-muted mb-3"></i>
                    <p class="text-muted">No signals for Dec 2025+</p>
                    <small class="text-muted">Check back later or try a different symbol</small>
                </div>
            `;
                return;
            }

            // Separate past and future signals
            const now = new Date();
            const futureSignals = allSignals.filter(s => s.x >= now);

            // Group signals by type for separate datasets
            const gannData = futureSignals.filter(s => s.type === 'gann');
            const fibData = futureSignals.filter(s => s.type === 'fibonacci' || s.type === 'fib_alignment');
            const solarData = solarEvents;  // Use solarEvents directly (already filtered by cutoff)

            // Create timeline with THREE distinct colors
            const chart = new Chart(ctx, {
                type: 'scatter',
                data: {
                    datasets: [
                        // 1. GANN DATES - GREEN
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
                            pointRadius: 3,        // Small dots
                            pointHoverRadius: 8
                        },
                        // 2. FIBONACCI DATES - PURPLE
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
                            pointRadius: 2.5,      // Small dots
                            pointHoverRadius: 7
                        },
                        // 3. SOLAR EVENTS - YELLOW/ORANGE
                        {
                            label: `☀️ Solar AP ≥ 12 (${solarData.length})`,
                            data: solarData.map(signal => ({
                                x: signal.x,
                                y: signal.y,
                                signal: signal
                            })),
                            backgroundColor: 'rgba(245, 158, 11, 1)', // Full opacity
                            borderColor: 'rgb(245, 158, 11)',
                            borderWidth: 2,                           // Thicker border
                            pointStyle: 'circle',
                            pointRadius: 2.5,                           // Larger for visibility
                            pointHoverRadius: 3.5
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
                                pointStyle: 'circle',
                                font: {
                                    size: 12
                                }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                title: (context) => {
                                    const point = context[0].raw;
                                    const signal = point.signal;
                                    const dateStr = signal.x.toLocaleDateString('en-US', {
                                        year: 'numeric',
                                        month: 'short',
                                        day: 'numeric',
                                        weekday: 'short'
                                    });

                                    switch(signal.type) {
                                        case 'gann':
                                            return `📅 Gann Date: ${dateStr}`;
                                        case 'fibonacci':
                                        case 'fib_alignment':
                                            return signal.projection ?
                                                `🔷 ${signal.ratioLabel || 'Fibonacci'}: ${dateStr}` :
                                                `🔷 Fibonacci Alignment: ${dateStr}`;
                                        case 'solar':
                                            return `☀️ Solar AP ${signal.event.ap}: ${dateStr}`;
                                        default:
                                            return `Signal: ${dateStr}`;
                                    }
                                },
                                label: (context) => {
                                    const point = context.raw;
                                    const signal = point.signal;

                                    switch(signal.type) {
                                        case 'gann':
                                            const gannFactors = signal.alignment.contributingFactors || [];
                                            const gannSignals = gannFactors.filter(f => f.includes('_ANNIVERSARY'));
                                            return [
                                                `Gann Anniversary${gannSignals.length > 1 ? 's' : ''}`,
                                                `Signals: ${gannSignals.length}`,
                                                gannSignals.map(f => f.replace('_ANNIVERSARY', 'D')).join(', ')
                                            ];
                                        case 'fibonacci':
                                            if (signal.projection) {
                                                // ✅ FIXED: Show Fib 0.618 instead of F13
                                                return [
                                                    `${signal.ratioLabel || 'Fibonacci'}`,
                                                    `Type: ${signal.projection.type || 'N/A'}`,
                                                    `${signal.days || signal.projection.fibonacciNumber} days`,
                                                    `From: ${signal.projection.sourcePivot?.type || 'pivot'} at $${signal.projection.sourcePivot?.price?.toFixed(2) || 'N/A'}`
                                                ];
                                            } else {
                                                const fibFactors = signal.alignment.contributingFactors || [];
                                                const fibSignals = fibFactors.filter(f => f.startsWith('FIB_'));
                                                // ✅ FIXED: Convert FIB_13 to Fib 0.13
                                                const fibLabels = fibSignals.map(f => {
                                                    const fibNumber = parseInt(f.replace('FIB_', ''));
                                                    const ratio = fibNumber / 100.0;
                                                    return this.getFibRatioLabel(ratio, fibNumber);
                                                });
                                                return [
                                                    `Fibonacci Alignment`,
                                                    `Signals: ${fibSignals.length}`,
                                                    fibLabels.join(', ')
                                                ];
                                            }
                                        case 'fib_alignment':
                                            if (signal.ratioLabels) {
                                                return [
                                                    `Fibonacci Alignment`,
                                                    `Signals: ${signal.ratioLabels.length}`,
                                                    signal.ratioLabels.join(', ')
                                                ];
                                            } else {
                                                const fibFactors = signal.alignment.contributingFactors || [];
                                                const fibSignals = fibFactors.filter(f => f.startsWith('FIB_'));
                                                const fibLabels = fibSignals.map(f => {
                                                    const fibNumber = parseInt(f.replace('FIB_', ''));
                                                    const ratio = fibNumber / 100.0;
                                                    return this.getFibRatioLabel(ratio, fibNumber);
                                                });
                                                return [
                                                    `Fibonacci Alignment`,
                                                    `Signals: ${fibSignals.length}`,
                                                    fibLabels.join(', ')
                                                ];
                                            }
                                        case 'solar':
                                            return [
                                                `Solar Geomagnetic Activity`,
                                                `AP Index: ${signal.event.ap}`,
                                                `High AP Day (≥12)`
                                            ];
                                        default:
                                            return [`Signal: ${signal.type}`];
                                    }
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
                                text: 'Dec 2025 - Aug 2026 Timeline',
                                font: {
                                    size: 14,
                                    weight: 'bold'
                                }
                            },
                            min: '2025-12-01',
                            max: '2026-08-31',
                            grid: {
                                color: 'rgba(0, 0, 0, 0.05)',
                                drawBorder: true
                            },
                            ticks: {
                                maxRotation: 0,
                                autoSkip: true,
                                maxTicksLimit: 12,
                                minRotation: 0,
                                padding: 0
                            },
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
                            radius: 3,
                            hoverRadius: 8,
                            hitRadius: 10
                        }
                    }
                }
            });

            this.charts.set('timeline', chart);

            // Legend explaining colors
            const noteDiv = document.createElement('div');
            noteDiv.className = 'alert alert-info mt-3';
            noteDiv.innerHTML = `
            <div class="row">
                <div class="col-md-4">
                    <span class="badge" style="background-color: rgba(16, 185, 129, 0.8); color: white;">
                        📅 Gann Dates
                    </span>
                    <small class="ms-2">Gann anniversary alignments</small>
                </div>
                <div class="col-md-4">
                    <span class="badge" style="background-color: rgba(79, 70, 229, 0.8); color: white;">
                        🔷 Fibonacci
                    </span>
                    <small class="ms-2">Fibonacci projections & alignments</small>
                </div>
                <div class="col-md-4">
                    <span class="badge" style="background-color: rgba(245, 158, 11, 0.8); color: black;">
                        ☀️ Solar AP ≥ 12
                    </span>
                    <small class="ms-2">High geomagnetic activity days</small>
                </div>
            </div>
            <div class="mt-2 small">
                <i class="fas fa-filter me-1"></i>
                Showing ${futureSignals.length} future signals from Dec 2025 to Aug 2026
            </div>
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

    getFibRatioLabel(ratio, days) {
        console.log(`🔍 getFibRatioLabel called with: ratio=${ratio}, days=${days}`);

        // ===== ROUNDING CORRECTIONS =====
        // Apply corrections for known rounding issues
        const roundingCorrections = {
            79: 0.786,   // 0.786 * 100 = 78.6 → rounded to 79
            62: 0.618,   // 0.618 * 100 = 61.8 → rounded to 62
            38: 0.382,   // 0.382 * 100 = 38.2 → rounded to 38
            50: 0.500,   // 0.500 * 100 = 50.0 → exact
            127: 1.272,  // 1.272 * 100 = 127.2 → rounded to 127
            162: 1.618,  // 1.618 * 100 = 161.8 → rounded to 162
            262: 2.618   // 2.618 * 100 = 261.8 → rounded to 262
        };

        // Apply correction if days match a known rounding case
        if (roundingCorrections[days]) {
            const correctRatio = roundingCorrections[days];
            const ratioDiff = Math.abs(ratio - correctRatio);

            // Only apply if significantly wrong (more than 0.2% difference)
            if (ratioDiff > 0.002) {
                console.log(`🔄 Correcting: ${ratio.toFixed(3)} (${days}d) → ${correctRatio}`);
                ratio = correctRatio;
            }
        }

        // If ratio is still 0.790 after correction, log warning
        if (Math.abs(ratio - 0.790) < 0.001) {
            console.warn(`🚨 NON-FIBONACCI RATIO DETECTED: ${ratio} (${days} days)`);
            console.trace();
        }

        // ===== RATIO MAPPING =====
        const ratioMap = {
            0.382: 'Fib 0.382',
            0.500: 'Fib 0.500',
            0.618: 'Fib 0.618',
            0.786: 'Fib 0.786',
            1.000: 'Fib 1.000',
            1.272: 'Fib 1.272',
            1.618: 'Fib 1.618',
            2.618: 'Fib 2.618'
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

        // Fallback
        return `Fib ${roundedRatio.toFixed(3)}`;
    }

    // Refresh all data
    refreshData() {
        this.loadAllData(this.currentSymbol);

        // Update timestamp
        this.lastUpdated = new Date();
        document.getElementById('lastUpdated').textContent = this.lastUpdated.toLocaleTimeString();
    }

    // Helper: Format date
    formatDate(dateString) {
        if (!dateString) return 'Unknown';

        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }

    // Helper: Days from now
    daysFromNow(dateString) {
        if (!dateString) return '?';

        const date = new Date(dateString);
        const now = new Date();
        const diffTime = date - now;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        return diffDays > 0 ? `+${diffDays}d` : diffDays === 0 ? 'Today' : `${diffDays}d ago`;
    }
}

// Initialize dashboard when page loads
window.addEventListener('load', function() {
    try {
        console.log('📱 Initializing Fibonacci Time Trader Dashboard...');

        // Create dashboard instance
        window.dashboard = new TimeGeometryDashboard();

        // Load initial data
        setTimeout(() => {
            try {
                window.dashboard.loadAllData('BTC');

                // Set initial timestamp
                document.getElementById('lastUpdated').textContent = new Date().toLocaleTimeString();

            } catch (error) {
                console.error('Failed to load initial data:', error);
            }
        }, 100);

    } catch (error) {
        console.error('❌ Failed to initialize dashboard:', error);

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