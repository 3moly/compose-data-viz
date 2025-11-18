// Prevent trackpad pinch zoom
document.addEventListener('wheel', function(e) {
    if (e.ctrlKey) {
        e.preventDefault(); // Ctrl+wheel is used for zooming
    }
}, { passive: false });

// Prevent gesture-based zoom
document.addEventListener('gesturestart', function(e) {
    e.preventDefault();
});

document.addEventListener('gesturechange', function(e) {
    e.preventDefault();
});

document.addEventListener('gestureend', function(e) {
    e.preventDefault();
});