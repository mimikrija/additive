const tokenKey = 'additive-game-token';
const problemText = document.querySelector('#problem');
const answerForm = document.querySelector('#answer-form');
const answerInput = document.querySelector('#answer');
const checkButton = document.querySelector('#check');
const nextButton = document.querySelector('#next');
const feedback = document.querySelector('#feedback');
const level = document.querySelector('#level');
const streak = document.querySelector('#streak');
const icecreams = document.querySelector('#icecreams');

let token = localStorage.getItem(tokenKey);
let problem;
let pendingAnswer;

async function request(path, options = {}) {
    const headers = token ? { 'X-Game-Token': token } : {};
    const response = await fetch(`/api${path}`, { ...options, headers, cache: 'no-store' });
    if (response.status === 401) {
        token = null;
        localStorage.removeItem(tokenKey);
    }
    if (!response.ok) {
        throw new Error(`Game request failed: ${response.status}`);
    }
    return response.json();
}

async function refreshProgress() {
    try {
        const game = await request('/me/progress');
        level.textContent = game.progress.difficulty.replace('LEVEL_', '');
        streak.textContent = game.progress.correctStreak;
        icecreams.textContent = game.icecreams;
    } catch {
        level.textContent = streak.textContent = icecreams.textContent = '–';
    }
}

async function loadProblem() {
    nextButton.disabled = true;
    feedback.textContent = '';
    problemText.textContent = 'Loading a problem…';
    try {
        if (!token) {
            ({ token } = await request('/children', { method: 'POST' }));
            localStorage.setItem(tokenKey, token);
        }
        problem = await request('/me/problem', { method: 'POST' });
        const symbol = problem.type === 'ADDITION' ? '+' : '−';
        problemText.textContent = `${problem.first} ${symbol} ${problem.second} = ?`;
        await refreshProgress();
        pendingAnswer = null;
        answerInput.value = '';
        answerInput.readOnly = false;
        answerForm.hidden = false;
        nextButton.hidden = true;
        answerInput.focus();
    } catch {
        problemText.textContent = token ? 'Could not load a problem.' : 'Could not resume. Start a game to play.';
        nextButton.textContent = token ? 'Try again' : 'Start game';
    } finally {
        nextButton.disabled = false;
    }
}

answerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!problem || checkButton.disabled) return;

    pendingAnswer ??= new URLSearchParams({
        attemptId: crypto.randomUUID(),
        problemId: problem.id,
        answer: answerInput.value,
    });
    checkButton.disabled = true;
    answerInput.readOnly = true;
    feedback.textContent = '';

    try {
        const result = await request('/me/attempts', { method: 'POST', body: pendingAnswer });
        pendingAnswer = null;
        await refreshProgress();
        if (result.correct || !result.counted) {
            answerForm.hidden = true;
            nextButton.textContent = 'Next problem';
            nextButton.hidden = false;
            nextButton.focus();
            feedback.textContent = result.rewarded ? 'Correct! You earned an ice cream.' :
                result.counted ? 'Correct!' : 'This problem was already solved.';
        } else {
            feedback.textContent = 'Not quite. Try again!';
            answerInput.readOnly = false;
            answerInput.select();
        }
    } catch {
        if (!token) {
            answerForm.hidden = true;
            nextButton.textContent = 'Start game';
            nextButton.hidden = false;
            feedback.textContent = 'Could not resume your saved game.';
        } else {
            feedback.textContent = 'Could not check this answer. Press Check answer to retry.';
        }
    } finally {
        checkButton.disabled = false;
    }
});

nextButton.addEventListener('click', loadProblem);
if (token) loadProblem();
