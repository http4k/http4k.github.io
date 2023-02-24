// toggle search via command + k
$(window).keydown(function(event) {
    console.log("A key is triggered: " + event.keyCode)
    if (event.metaKey && (event.keyCode === 75)) {
        event.preventDefault()
        $("#search").focus();
    }
});
