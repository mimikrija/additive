const problem = document.querySelector('#problem');
const answerForm = document.querySelector('#answer-form');
const answerInput = document.querySelector('#answer');
const checkButton = document.querySelector('#check');
const nextButton = document.querySelector('#next');
const feedback = document.querySelector('#feedback');

let currentProblem;

async function loadProblem() {
    problem.textContent = 'Loading a problem…';
    feedback.textContent = '';
    answerInput.value = '';
    answerInput.disabled = true;
    checkButton.disabled = true;
    nextButton.disabled = true;

    try {
        const response = await fetch('/api/problems/addition', { cache: 'no-store' });
        if (!response.ok) {
            throw new Error('Could not load a problem');
        }

        currentProblem = await response.json();
        problem.textContent = `${currentProblem.first} + ${currentProblem.second} = ?`;
        answerInput.disabled = false;
        checkButton.disabled = false;
    } catch {
        problem.textContent = 'Could not load a problem. Try New problem.';
    } finally {
        nextButton.disabled = false;
    }
}

answerForm.addEventListener('submit', (event) => {
    event.preventDefault();
    feedback.textContent = answerInput.valueAsNumber === currentProblem.answer
        ? 'Correct!'
        : 'Not quite. Try again!';
});

nextButton.addEventListener('click', loadProblem);

loadProblem();
