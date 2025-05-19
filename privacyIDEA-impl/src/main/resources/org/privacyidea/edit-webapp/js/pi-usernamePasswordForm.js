// Request a passkey login. This is activated by the passkey button.
function passkeyButtonListener()
{
    if (typeof (document.getElementById('initPasskeyLoginWithoutUsername')) != 'undefined'
        && document.getElementById('initPasskeyLoginWithoutUsername') != null)
    {
        document.getElementById("initPasskeyLoginWithoutUsername").addEventListener("click", () => {
            piSetValue("passkeyLoginRequested", 1);
            //piSubmit();
        });
    }
}


// Wait until the document is ready
document.addEventListener("DOMContentLoaded", function ()
{
    passkeyButtonListener();
});