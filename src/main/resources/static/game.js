const tokenKey = 'additive-game-token';
const problemText = document.querySelector('#problem');
const answers = document.querySelector('#answers');
const choices = document.querySelector('#choices');
const nextButton = document.querySelector('#next');
const skipButton = document.querySelector('#skip');
const feedback = document.querySelector('#feedback');
const level = document.querySelector('#level');
const streak = document.querySelector('#streak');
const bestStreak = document.querySelector('#best-streak');
const icecreams = document.querySelector('#icecreams');
const icecreamsLabel = document.querySelector('#icecreams-label');

let token = localStorage.getItem(tokenKey);
let problem;
let pendingAnswer;
let busy = false;

function showFeedback(message = '', tone = '') {
    feedback.textContent = message;
    feedback.dataset.tone = tone;
}

function updateControls() {
    answers.hidden = !problem;
    nextButton.hidden = Boolean(problem);
    nextButton.disabled = busy;
    skipButton.hidden = !problem?.triedChoices.length;
    skipButton.disabled = busy || Boolean(pendingAnswer);
    for (const button of choices.children) {
        const value = Number(button.value);
        const tried = problem?.triedChoices.includes(value);
        button.disabled = busy || !problem || tried || (pendingAnswer && pendingAnswer.value !== value);
        button.classList.toggle('line-through', Boolean(tried));
    }
}

async function request(path, options = {}) {
    const headers = token ? { 'X-Game-Token': token } : {};
    const response = await fetch(`/api${path}`, { ...options, headers, cache: 'no-store' });
    if (response.status === 401) {
        token = null;
        localStorage.removeItem(tokenKey);
    }
    if (!response.ok) {
        const error = new Error(`Game request failed: ${response.status}`);
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? undefined : response.json();
}

async function refreshProgress() {
    try {
        const game = await request('/me/progress');
        level.textContent = game.progress.difficulty.replace('LEVEL_', '');
        streak.textContent = game.currentCorrectStreak;
        bestStreak.textContent = game.bestCorrectStreak;
        icecreams.textContent = `${game.icecreams}× 🍦`;
        icecreamsLabel.textContent = `${game.icecreams} ${game.icecreams === 1 ? 'ice cream' : 'ice creams'}`;
    } catch {
        level.textContent = streak.textContent = bestStreak.textContent = icecreams.textContent = '–';
        icecreamsLabel.textContent = 'Count unavailable';
    }
}

async function loadProblem() {
    busy = true;
    problem = null;
    pendingAnswer = null;
    updateControls();
    showFeedback();
    problemText.textContent = 'Loading a problem…';
    try {
        if (!token) {
            ({ token } = await request('/children', { method: 'POST' }));
            localStorage.setItem(tokenKey, token);
        }
        problem = await request('/me/problem', { method: 'POST' });
        const symbol = problem.type === 'ADDITION' ? '+' : '−';
        problemText.textContent = `${problem.first} ${symbol} ${problem.second} = ?`;
        choices.replaceChildren(...problem.choices.map((value) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'answer-choice';
            button.value = button.textContent = value;
            return button;
        }));
        await refreshProgress();
    } catch {
        problemText.textContent = token ? 'Could not load a problem.' : 'Ready to play?';
        showFeedback('Could not load the game. Try again.', 'error');
        nextButton.textContent = token ? 'Try again' : 'Start game';
    } finally {
        busy = false;
        updateControls();
        if (problem) choices.querySelector('button:enabled')?.focus();
    }
}

function resetUnavailableGame() {
    problem = null;
    pendingAnswer = null;
    problemText.textContent = 'Ready to play?';
    nextButton.textContent = 'Start game';
    showFeedback('Could not resume your saved game.', 'error');
}

choices.addEventListener('click', async (event) => {
    const button = event.target.closest('button');
    if (!button || button.disabled || busy || !problem) return;

    const value = Number(button.value);
    pendingAnswer ??= { id: crypto.randomUUID(), value };
    busy = true;
    updateControls();
    showFeedback();

    try {
        const body = new URLSearchParams({ attemptId: pendingAnswer.id, problemId: problem.id, answer: value });
        const result = await request('/me/attempts', { method: 'POST', body });
        pendingAnswer = null;
        await refreshProgress();
        if (!result.counted || result.correct) {
            problem = null;
            nextButton.textContent = 'Next problem';
            if (!result.counted) {
                showFeedback('This problem is already closed.');
            } else {
                showFeedback(result.rewarded ? 'Correct! You earned an ice cream.' : 'Correct!', 'success');
            }
        } else {
            problem.triedChoices.push(value);
            showFeedback('Not quite. Try another answer or skip.', 'retry');
        }
    } catch (error) {
        if (!token) {
            resetUnavailableGame();
        } else if (error.status >= 400 && error.status < 500) {
            problem = null;
            pendingAnswer = null;
            nextButton.textContent = 'Reload problem';
            showFeedback('Could not use that answer. Reload the problem and try again.', 'error');
        } else {
            showFeedback(`Could not check your answer. Tap ${value} to retry.`, 'error');
        }
    } finally {
        busy = false;
        updateControls();
        (problem ? choices.querySelector('button:enabled') : nextButton)?.focus();
    }
});

skipButton.addEventListener('click', async () => {
    if (busy || !problem || !problem.triedChoices.length || pendingAnswer) return;

    busy = true;
    updateControls();
    try {
        const body = new URLSearchParams({ problemId: problem.id });
        await request('/me/problem/skip', { method: 'POST', body });
    } catch (error) {
        if (!token) {
            resetUnavailableGame();
        } else if (error.status === 409) {
            await loadProblem();
            return;
        } else {
            showFeedback('Could not skip this problem. Try again.', 'error');
        }
        busy = false;
        updateControls();
        (problem ? skipButton : nextButton).focus();
        return;
    }
    await loadProblem();
});

nextButton.addEventListener('click', loadProblem);
if (token) loadProblem();
