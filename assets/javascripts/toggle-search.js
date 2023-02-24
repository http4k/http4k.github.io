// toggle search via command + k
$(window).keydown(function(event) {
    if (event.metaKey) {
        console.log("a.t")

    }
    console.log("A key is triggered: " + event.keyCode)
    if (event.metaKey && (event.keyCode === 75)) {
        event.preventDefault()
        console.log("HEY!")
        $("#search").focus();
    }
});

$(window).keyup(function(event) {
    if (event.metaKey) {
        console.log("keyup")

    }
    console.log("A key is triggered: " + event.keyCode)
    if (event.metaKey && (event.keyCode === 75)) {
        console.log("HEY!")
        $("#search").focus();
    }
});