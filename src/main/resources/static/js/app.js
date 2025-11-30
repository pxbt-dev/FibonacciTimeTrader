class TimeGeometryApp {
    constructor() {
        this.currentSymbol = 'BTC';
        this.init();
    }

    init() {
        this.bindEvents();
        this.loadData(this.currentSymbol);
    }

    bindEvents() {
        // Symbol selector
        document.querySelectorAll('.symbol-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                document.querySelectorAll('.symbol-btn').forEach(b => b.classList.remove('active'));
                e.target.classList.add('active');
                this.currentSymbol = e.target.dataset.symbol;
                this.loadData(this.currentSymbol);
            });
        });
    }

    async loadData(symbol) {
        this.showLoading();
        this.hideError();
        this.hideResults();

        try {
            const response = await fetch(`/api/time-geometry/analysis/${symbol}`);
            if (!response.ok) throw new Error('API request failed');

            const data = await response.json();
            this.displayData(data);
        } catch (error) {
            console.error('Error loading data:', error);
            this.showError();
        }
    }

    displayData(data) {
        this.hideLoading();
        this.showResults();

        this.displayNextVortex(data);
        this.createFibonacciTimeline(data);
        this.displayVortexCalendar(data);
        this.displayProjections(data);
    }

    displayNextVortex(data) {
        const vortexContainer = document.getElementById('nextVortex');
        const nextVortex = data.vortexWindows && data.vortexWindows.length > 0
            ? data.vortexWindows[0]
            : null;

        if (nextVortex) {
            vortexContainer.innerHTML = `
                <div class="vortex-date">${this.formatDate(nextVortex.date)}</div>
                <div class="vortex-intensity">Intensity: ${(nextVortex.intensity * 100).toFixed(0)}%</div>
                <div class="vortex-description">${nextVortex.description}</div>
                <div class="vortex-factors">
                    ${nextVortex.contributingFactors.map(factor =>
                `<span class="factor-tag">${factor}</span>`
            ).join('')}
                </div>
            `;
        } else {
            vortexContainer.innerHTML = `
                <div class="vortex-date">No upcoming vortex windows</div>
                <div class="vortex-description">Check back later for new time geometry signals</div>
            `;
        }
    }

    createFibonacciTimeline(data) {
        const container = document.getElementById('fibonacciTimeline');
        const projections = data.fibonacciTimeProjections || [];

        if (projections.length === 0) {
            container.innerHTML = '<p class="no-data">No Fibonacci projections available</p>';
            return;
        }

        // Sort by date
        projections.sort((a, b) => new Date(a.date) - new Date(b.date));

        let html = `
            <div class="timeline">
                <div class="timeline-header">
                    <span>Date</span>
                    <span>Fibonacci</span>
                    <span>Intensity</span>
                    <span>Type</span>
                </div>
        `;

        projections.forEach(proj => {
            const intensityPercent = (proj.intensity * 100).toFixed(0);
            const intensityClass = proj.intensity > 0.7 ? 'high' : proj.intensity > 0.4 ? 'medium' : 'low';

            html += `
                <div class="timeline-item">
                    <div class="timeline-date">${this.formatDate(proj.date)}</div>
                    <div class="timeline-fib">F${proj.fibonacciNumber}</div>
                    <div class="timeline-intensity ${intensityClass}">${intensityPercent}%</div>
                    <div class="timeline-type ${proj.type.toLowerCase()}">${proj.type}</div>
                </div>
            `;
        });

        html += '</div>';
        container.innerHTML = html;
    }

    displayVortexCalendar(data) {
        const calendarContainer = document.getElementById('vortexCalendar');
        const vortexWindows = data.vortexWindows || [];

        if (vortexWindows.length === 0) {
            calendarContainer.innerHTML = '<p class="no-data">No vortex windows in the near future.</p>';
            return;
        }

        calendarContainer.innerHTML = vortexWindows.map(vortex => `
            <div class="calendar-item">
                <div class="calendar-date">${this.formatDate(vortex.date)}</div>
                <div class="calendar-intensity">Intensity: ${(vortex.intensity * 100).toFixed(0)}%</div>
                <div class="calendar-type">${vortex.type}</div>
                <div class="calendar-factors">
                    ${vortex.contributingFactors.join(', ')}
                </div>
            </div>
        `).join('');
    }

    displayProjections(data) {
        // Fibonacci projections
        const fibList = document.getElementById('fibonacciList');
        const fibProjections = data.fibonacciTimeProjections || [];

        fibList.innerHTML = fibProjections.map(proj => `
            <div class="projection-item">
                <div class="projection-date">${this.formatDate(proj.date)} - F${proj.fibonacciNumber}</div>
                <div class="projection-desc">${proj.description} (${(proj.intensity * 100).toFixed(0)}%)</div>
            </div>
        `).join('');

        // Gann dates
        const gannList = document.getElementById('gannList');
        const gannDates = data.gannDates || [];

        gannList.innerHTML = gannDates.map(gann => `
            <div class="projection-item">
                <div class="projection-date">${this.formatDate(gann.date)} - ${gann.type}</div>
                <div class="projection-desc">From ${gann.sourcePivot.type.toLowerCase()} at $${gann.sourcePivot.price.toLocaleString()}</div>
            </div>
        `).join('');
    }

    formatDate(dateString) {
        return new Date(dateString).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }

    showLoading() {
        document.getElementById('loading').style.display = 'block';
    }

    hideLoading() {
        document.getElementById('loading').style.display = 'none';
    }

    showResults() {
        document.getElementById('results').style.display = 'block';
    }

    hideResults() {
        document.getElementById('results').style.display = 'none';
    }

    showError() {
        document.getElementById('error').style.display = 'block';
        this.hideLoading();
    }

    hideError() {
        document.getElementById('error').style.display = 'none';
    }
}

// Initialize app when page loads
document.addEventListener('DOMContentLoaded', () => {
    new TimeGeometryApp();
});