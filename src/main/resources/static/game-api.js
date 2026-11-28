export const gameTokenKey = 'additive-game-token';

export async function requestGame(path, token, options = {}) {
    const headers = token ? { 'X-Game-Token': token } : {};
    const response = await fetch(`/api${path}`, { ...options, headers, cache: 'no-store' });
    if (!response.ok) {
        const error = new Error(`Game request failed: ${response.status}`);
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? undefined : response.json();
}
