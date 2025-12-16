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

    // Load ALL data (time geometry + solar) in parallel
    async loadAllData(symbol) {
        console.log(`📊 Loading ALL data for ${symbol}...`);

        try {
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

            const analysis = await timeGeometryResponse.json();

            // Load solar data if available
            if (solarResponse.ok) {
                this.solarData = await solarResponse.json();
            } else {
                console.warn('Solar API failed, continuing without solar data');
                this.solarData = null;
            }

            console.log('✅ ALL data loaded');

            // Update all UI components in order
            this.createTimelineChart(analysis);
            this.renderAlignmentDates(analysis.vortexWindows || []);
            this.renderFibonacciLevels(analysis.fibonacciPriceLevels || []);
            this.loadGannDates(symbol);
            this.renderSolarDashboard();

            console.log('📊 Analysis data:', {
                vortexWindows: analysis.vortexWindows?.length || 0,
                fibonacciPriceLevels: analysis.fibonacciPriceLevels?.length || 0
            });

        } catch (error) {
            console.error('❌ Failed to load ALL data:', error);
            this.showError(error);
        }
    }

    // FIXED: Load Gann dates with proper time range
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

    // FIXED: Render Gann dates including future dates
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
                        
                        <div class="description small text-muted">
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
                        <div class="description text-muted">
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

        // Render resistance levels (LEFT COLUMN)
        if (resistanceLevels.length > 0) {
            // Sort resistance by price (lowest to highest)
            resistanceLevels.sort((a, b) => a.price - b.price);

            resistanceContainer.innerHTML = resistanceLevels.map(level => {
                const formattedPrice = `$${this.formatPrice(level.price)}`;
                const isKeyLevel = level.ratio === 1.618 || level.ratio === 2.618;

                return `
                <div class="date-card ${isKeyLevel ? 'key-level border-success' : 'border-success'}">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <div>
                            <span class="badge ${isKeyLevel ? 'bg-success' : 'bg-success'}">
                                ${level.label}
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
                            <span class="badge bg-secondary">Ratio: ${level.ratio.toFixed(3)}</span>
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
                const isKeyLevel = level.ratio === 0.618 || level.ratio === 0.786;

                return `
                <div class="date-card ${isKeyLevel ? 'key-level border-danger' : 'border-danger'}">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <div>
                            <span class="badge ${isKeyLevel ? 'bg-danger' : 'bg-danger'}">
                                ${level.label}
                            </span>
                            ${isKeyLevel ? '<span class="badge bg-info ms-1">Key</span>' : ''}
                        </div>
                        <div class="h5 mb-0 text-danger">
                            ${formattedPrice}
                        </div>
                    </div>
                    <div class="description">
                        <i class="fas fa-arrow-down me-1 text-danger"></i>
                        ${level.distanceFromHigh} from cycle high
                        <div class="mt-1 small">
                            <span class="badge bg-secondary">Ratio: ${level.ratio.toFixed(3)}</span>
                            ${level.ratio === 0 ? '<span class="badge bg-dark ms-1">Cycle High</span>' : ''}
                            ${level.ratio === 1.0 ? '<span class="badge bg-dark ms-1">Cycle Low</span>' : ''}
                        </div>
                    </div>
                </div>
                `;
            }).join('');
        } else {
            supportContainer.innerHTML = '<div class="alert alert-info">No support levels available</div>';
        }
    }

    // FIXED: Enhanced alignment dates rendering with expandable details
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
                            <small class="text-muted me-2">Signals:</small>
                            ${uniqueSignals.slice(0, 3).map(signal =>
                `<span class="badge ${signal.type === 'fibonacci' ? 'bg-primary' : signal.type === 'gann' ? 'bg-success' : 'bg-secondary'} me-1">${signal.label}</span>`
            ).join('')}
                            ${uniqueSignals.length > 3 ? `<span class="badge bg-dark">+${uniqueSignals.length - 3}</span>` : ''}
                        </div>
                    </div>
                    
                    <div class="description mb-2">
                        <div class="mb-1">
                            ${fibSignals.length > 0 ? `<span class="text-primary">${fibSignals.length} Fibonacci</span>` : ''}
                            ${gannSignals.length > 0 ? `<span class="text-success">${gannSignals.length} Gann</span>` : ''}
                            ${otherSignals.length > 0 ? `<span class="text-warning">${otherSignals.length} Other</span>` : ''}
                        </div>
                        <div class="small text-muted">
                            ${this.daysFromNow(group.date)}
                        </div>
                    </div>
                    
                    <!-- Expandable details for "other signals" -->
                    ${otherSignals.length > 0 ? `
                        <div class="additional-alignments mt-2">
                            <button class="btn btn-sm btn-outline-secondary btn-toggle w-100" 
                                    onclick="this.nextElementSibling.classList.toggle('d-none'); this.querySelector('i').classList.toggle('fa-rotate-90')">
                                <i class="fas fa-chevron-right fa-xs"></i>
                                Show ${otherSignals.length} other signal${otherSignals.length > 1 ? 's' : ''}
                            </button>
                            <div class="d-none mt-2">
                                ${otherSignals.map(signal => `
                                    <div class="additional-alignment small mb-1 p-2 bg-dark rounded">
                                        <i class="fas fa-info-circle me-1"></i>
                                        ${signal.rawFactor || signal.label}
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                    ` : ''}
                    
                    <!-- Expandable additional alignments if multiple -->
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
                    `<span class="badge ${s.type === 'fibonacci' ? 'bg-primary' : s.type === 'gann' ? 'bg-success' : 'bg-secondary'} me-1">${s.label}</span>`
                ).join('')}
                                                    </div>
                                                    <span class="badge ${alignment.intensity > 0.8 ? 'bg-danger' : alignment.intensity > 0.5 ? 'bg-warning' : 'bg-info'}">
                                                        ${(alignment.intensity * 100).toFixed(0)}%
                                                    </span>
                                                </div>
                                                ${alignment.description ? `<div class="text-muted mt-1 small">${alignment.description}</div>` : ''}
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
            const cutoffDate = new Date('2025-12-01T00:00:00Z');
            const endDate = new Date('2026-12-31T00:00:00Z');

            // Get solar data if available
            const solarHighApDays = this.solarData?.forecast || [];

            // Process solar dates
            const solarEvents = solarHighApDays
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

            const allAlignmentDates = analysis.vortexWindows || [];
            const allFibonacciProjections = analysis.fibonacciTimeProjections || [];

            // Load Gann dates separately
            this.loadGannDataForChart().then(gannDates => {
                console.log('📊 Gann dates for chart:', gannDates.length);

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

                // Combine all signals including standalone Gann dates
                const allSignals = [
                    ...gannDates,
                    ...processedAlignments,
                    ...standaloneFibonacci,
                    ...solarEvents
                ].sort((a, b) => a.x - b.x);

                console.log('📊 Total signals for chart:', {
                    gann: gannDates.length,
                    alignments: processedAlignments.length,
                    fibonacci: standaloneFibonacci.length,
                    solar: solarEvents.length,
                    total: allSignals.length
                });

                if (allSignals.length === 0) {
                    ctx.parentElement.innerHTML = `
                        <div class="text-center py-4">
                            <i class="fas fa-filter fa-2x text-muted mb-3"></i>
                            <p class="text-muted">No signals for Dec 2025+</p>
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
                const solarData = solarEvents;

                // Create the chart
                this.createChart(ctx, gannData, fibData, solarData, futureSignals.length);
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
                    y: 85, // Fixed intensity for standalone Gann dates
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
    createChart(ctx, gannData, fibData, solarData, totalSignals) {
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
                            text: 'Dec 2025 - Dec 2026 Timeline',
                            color: '#f1f5f9',
                            font: {
                                size: 14,
                                weight: 'bold'
                            }
                        },
                        min: '2025-12-01',
                        max: '2026-12-31',
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
                return [
                    `${signal.ratioLabel || 'Fibonacci'}`,
                    `Type: ${signal.projection.type || 'N/A'}`,
                    `${signal.days || signal.projection.fibonacciNumber} days`,
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
            default:
                return [`Signal: ${signal.type}`];
        }
    }

    // Render solar dashboard
    renderSolarDashboard() {
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
                
                <div class="col-md-4 mb-3">
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
                
                <div class="col-md-4 mb-3">
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
                        Next high AP day: ${this.formatDate(highApDays[0]?.date)} (${this.daysFromNow(highApDays[0]?.date)})
                    </div>
                </div>
                
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
            ` : `
                <div class="alert alert-success">
                    <i class="fas fa-check-circle me-2"></i>
                    <strong>Quiet geomagnetic period</strong>
                    <div class="mt-1 small">No days with AP ≥ 12 in the forecast</div>
                </div>
            `}
        `;
    }

    // Helper methods
    parseConvergingSignals(factors) {
        const signals = [];

        factors.forEach((factor) => {
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
                    days: days,
                    rawFactor: factor
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
            else {
                signals.push({
                    type: 'other',
                    label: factor.replace('SOLAR_AP_', 'Solar AP '),
                    rawFactor: factor
                });
            }
        });

        return signals;
    }

    getFibRatioLabel(ratio, days) {
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

        for (const [key, label] of Object.entries(ratioMap)) {
            if (Math.abs(roundedRatio - parseFloat(key)) < 0.001) {
                return label;
            }
        }

        return `Fib ${roundedRatio.toFixed(3)}`;
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
        return diffDays > 0 ? `+${diffDays}d` : diffDays === 0 ? 'Today' : `${diffDays}d ago`;
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

// Initialize dashboard when page loads
window.addEventListener('load', function() {
    try {
        console.log('📱 Initializing Fibonacci Time Trader Dashboard...');
        window.dashboard = new TimeGeometryDashboard();

        setTimeout(() => {
            try {
                window.dashboard.loadAllData('BTC');
                window.dashboard.updateTimestamp();
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