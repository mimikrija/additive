import { gameTokenKey, requestGame } from './game-api.js';
import { initializeGameLanguage, setGameText } from './game-language.js';

const status = document.querySelector('#progress-status');
const summary = document.querySelector('#progress-summary');
const level = document.querySelector('#level');
const streak = document.querySelector('#streak');
const bestStreak = document.querySelector('#best-streak');
const icecreams = document.querySelector('#icecreams');
const icecreamsLabel = document.querySelector('#icecreams-label');
const accuracyPercent = document.querySelector('#accuracy-percent');
const accuracyCount = document.querySelector('#accuracy-count');
const accuracyWindow = document.querySelector('#accuracy-window');
let refreshing = false;

function showStatus(key) {
    if (status.dataset.text !== key) setGameText(status, key);
    status.hidden = !key;
}

function renderProgress(game) {
    level.textContent = game.progress.difficulty.replace('LEVEL_', '');
    streak.textContent = game.currentCorrectStreak;
    bestStreak.textContent = game.bestCorrectStreak;
    icecreams.textContent = `${game.icecreams}× 🍦`;
    setGameText(icecreamsLabel, 'icecreamCount', game.icecreams);

    const { firstTryCorrect, attempted, windowSize } = game.recentAccuracy;
    setGameText(accuracyWindow, 'accuracyWindow', windowSize);
    accuracyPercent.hidden = attempted === 0;
    if (attempted === 0) {
        setGameText(accuracyPercent);
        setGameText(accuracyCount, 'noAnswers');
    } else {
        setGameText(accuracyPercent, 'accuracyPercent', firstTryCorrect / attempted);
        delete accuracyCount.dataset.text;
        accuracyCount.textContent = `${firstTryCorrect} / ${attempted}`;
    }
    summary.hidden = false;
}

async function refreshProgress() {
    if (refreshing || document.hidden) return;

    const token = localStorage.getItem(gameTokenKey);
    if (!token) {
        summary.hidden = true;
        showStatus('noSavedGame');
        return;
    }

    refreshing = true;
    try {
        renderProgress(await requestGame('/me/progress', token));
        showStatus();
    } catch (error) {
        if (error.status === 401) {
            summary.hidden = true;
            showStatus('noSavedGame');
        } else {
            showStatus('progressUnavailable');
        }
    } finally {
        refreshing = false;
    }
}

initializeGameLanguage();
refreshProgress();
document.addEventListener('visibilitychange', refreshProgress);
setInterval(refreshProgress, 5000);
