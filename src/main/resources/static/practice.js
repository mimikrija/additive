const problem = document.querySelector('#problem');
const difficultySelect = document.querySelector('#difficulty');
const operationSelect = document.querySelector('#operation');
const answerForm = document.querySelector('#answer-form');
const answerInput = document.querySelector('#answer');
const checkButton = document.querySelector('#check');
const nextButton = document.querySelector('#next');
const feedback = document.querySelector('#feedback');

const operationSymbols = { ADDITION: '+', SUBTRACTION: '−', MULTIPLICATION: '×' };

let currentProblem;
let operationsByDifficulty;
let pendingProblem;

async function loadOptions() {
    const response = await fetch('/api/problems/options');
    if (!response.ok) {
        throw new Error('Could not load practice options');
    }

    operationsByDifficulty = await response.json();
    difficultySelect.replaceChildren(...Object.keys(operationsByDifficulty).map(
        (difficulty) => new Option(difficulty.replace('LEVEL_', 'Level '), difficulty),
    ));
    updateOperations();
    difficultySelect.disabled = false;
    operationSelect.disabled = false;
}

function updateOperations() {
    const previousOperation = operationSelect.value;
    const availableOperations = operationsByDifficulty[difficultySelect.value];
    operationSelect.replaceChildren(...availableOperations.map(
        (type) => new Option(type[0] + type.slice(1).toLowerCase(), type),
    ));
    if (availableOperations.includes(previousOperation)) {
        operationSelect.value = previousOperation;
    }
}

async function loadProblem() {
    pendingProblem?.abort();
    const request = new AbortController();
    pendingProblem = request;

    problem.textContent = 'Loading a problem…';
    feedback.textContent = '';
    answerInput.value = '';
    answerInput.disabled = true;
    checkButton.disabled = true;
    nextButton.disabled = true;

    try {
        if (!operationsByDifficulty) {
            await loadOptions();
        }
        const params = new URLSearchParams({ difficulty: difficultySelect.value, type: operationSelect.value });
        const response = await fetch(`/api/problems?${params}`, { cache: 'no-store', signal: request.signal });
        if (!response.ok) {
            throw new Error('Could not load a problem');
        }

        const nextProblem = await response.json();
        if (request.signal.aborted) {
            return;
        }

        currentProblem = nextProblem;
        const symbol = operationSymbols[currentProblem.type];
        problem.textContent = `${currentProblem.first} ${symbol} ${currentProblem.second} = ?`;
        answerInput.disabled = false;
        checkButton.disabled = false;
    } catch {
        if (!request.signal.aborted) {
            problem.textContent = 'Could not load a problem. Try New problem.';
        }
    } finally {
        if (!request.signal.aborted) {
            nextButton.disabled = false;
        }
    }
}

answerForm.addEventListener('submit', (event) => {
    event.preventDefault();
    feedback.textContent = answerInput.valueAsNumber === currentProblem.answer
        ? 'Correct!'
        : 'Not quite. Try again!';
});

difficultySelect.addEventListener('change', () => {
    updateOperations();
    return loadProblem();
});
operationSelect.addEventListener('change', loadProblem);
nextButton.addEventListener('click', loadProblem);

loadProblem();
