import { initializeGameLanguage, setGameText } from './game-language.js';
import { gameTokenKey, requestGame } from './game-api.js';

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

let token = localStorage.getItem(gameTokenKey);
let problem;
let pendingAnswer;
let busy = false;

function showFeedback(key, tone = '', value) {
    setGameText(feedback, key, value);
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

async function request(path, options) {
    try {
        return await requestGame(path, token, options);
    } catch (error) {
        if (error.status === 401) {
            token = null;
            localStorage.removeItem(gameTokenKey);
        }
        throw error;
    }
}

async function refreshProgress() {
    try {
        const game = await request('/me/progress');
        level.textContent = game.progress.difficulty.replace('LEVEL_', '');
        streak.textContent = game.currentCorrectStreak;
        bestStreak.textContent = game.bestCorrectStreak;
        icecreams.textContent = `${game.icecreams}× 🍦`;
        setGameText(icecreamsLabel, 'icecreamCount', game.icecreams);
    } catch {
        level.textContent = streak.textContent = bestStreak.textContent = icecreams.textContent = '–';
        setGameText(icecreamsLabel, 'countUnavailable');
    }
}

async function loadProblem() {
    busy = true;
    problem = null;
    pendingAnswer = null;
    updateControls();
    showFeedback();
    setGameText(problemText, 'loading');
    try {
        if (!token) {
            ({ token } = await request('/children', { method: 'POST' }));
            localStorage.setItem(gameTokenKey, token);
        }
        problem = await request('/me/problem', { method: 'POST' });
        const symbol = problem.type === 'ADDITION' ? '+' : '−';
        delete problemText.dataset.text;
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
        setGameText(problemText, token ? 'problemUnavailable' : 'ready');
        showFeedback('loadFailed', 'error');
        setGameText(nextButton, token ? 'tryAgain' : 'start');
    } finally {
        busy = false;
        updateControls();
        if (problem) choices.querySelector('button:enabled')?.focus();
    }
}

function resetUnavailableGame() {
    problem = null;
    pendingAnswer = null;
    setGameText(problemText, 'ready');
    setGameText(nextButton, 'start');
    showFeedback('resumeFailed', 'error');
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
            setGameText(nextButton, 'next');
            if (!result.counted) {
                showFeedback('closed');
            } else {
                showFeedback(result.rewarded ? 'rewarded' : 'correct', 'success');
            }
        } else {
            problem.triedChoices.push(value);
            showFeedback('wrong', 'retry');
        }
    } catch (error) {
        if (!token) {
            resetUnavailableGame();
        } else if (error.status >= 400 && error.status < 500) {
            problem = null;
            pendingAnswer = null;
            setGameText(nextButton, 'reload');
            showFeedback('invalidAnswer', 'error');
        } else {
            showFeedback('retryAnswer', 'error', value);
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
            showFeedback('skipFailed', 'error');
        }
        busy = false;
        updateControls();
        (problem ? skipButton : nextButton).focus();
        return;
    }
    await loadProblem();
});

nextButton.addEventListener('click', loadProblem);
initializeGameLanguage();
if (token) loadProblem();
