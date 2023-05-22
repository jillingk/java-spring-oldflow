const clientKey = document.getElementById("clientKey").innerHTML;
const type = document.getElementById("type").innerHTML;

async function initCheckout() {
  try {
    const paymentMethodsResponse = await callServer("/api/getPaymentMethods");
    const configuration = {
      paymentMethodsResponse: filterUnimplemented(paymentMethodsResponse),
      clientKey,
      locale: "en_US",
      environment: "test",
      showPayButton: true,
      paymentMethodsConfiguration: {
        ideal: {
          showImage: true,
        },
        card: {
          hasHolderName: true,
          holderNameRequired: true,
          name: "Credit or debit card",
          amount: {
            value: 1000,
            currency: "EUR",
          },
        },
        paypal: {
          amount: {
            value: 1000,
            currency: "USD",
          },
          environment: "test", // Change this to "live" when you're ready to accept live PayPal payments
          countryCode: "US", // Only needed for test. This will be automatically retrieved when you are in production.
          onCancel: (data, component) => {
            component.setStatus('ready');
          },
        }
      },
      // Why does this component return a data object that we cannot serialise as a PaymentRequest? (contain random params
      // like clientStateIndicator
      onSubmit: (state, component) => {
        if (state.isValid) {
          console.info(state, component);
          handleSubmission(state, component, "/api/initiatePayment");
        }
      },

      onAdditionalDetails: (state, component) => {
        console.info(state, component);
        handleSubmission(state, component, "/api/submitAdditionalDetails");
      },
    };
    // `spring.jackson.default-property-inclusion=non_null` needs to set in
    // src/main/resources/application.properties to avoid NPE here
    const checkout = await AdyenCheckout(configuration);
    checkout.create(type).mount(document.getElementById("payment"));
  } catch (error) {
    console.error(error);
    alert("Error occurred. Look at console for details");
  }
}

function filterUnimplemented(pm) {
  pm.paymentMethods = pm.paymentMethods.filter((it) =>
    [
      "scheme",
      "ideal",
      "dotpay",
      "giropay",
      // "sepadirectdebit",
      "directEbanking",
      "ach",
      "alipay",
      "klarna_paynow",
      "klarna",
      "klarna_account",
      "paypal",
    ].includes(it.type)
  );
  return pm;
}

// Event handlers called when the shopper selects the pay button,
// or when additional information is required to complete the payment
async function handleSubmission(state, component, url) {
  try {
    const res = await callServer(url, state.data);
    handleServerResponse(res, component);
  } catch (error) {
    console.error(error);
    alert("Error occurred. Look at console for details");
  }
}

// Calls your server endpoints
async function callServer(url, data) {
  const res = await fetch(url, {
    method: "POST",
    body: data ? JSON.stringify(data) : "",
    headers: {
      "Content-Type": "application/json",
    },
  });

  return await res.json();
}

// Handles responses sent from your server to the client
function handleServerResponse(res, component) {
  if (res.action) {
    let action = res.action.actualInstance;
    if(action.type = "REDIRECT"){
        // Why are we getting uppercase type here even though the component takes lowercase?
        action.type = "redirect"
    }
    component.handleAction(action);
  } else {
    switch (res.resultCode) {
      case "AUTHORISED":
        window.location.href = "/result/success";
        break;
      case "PENDING":
      case "RECEIVED":
        window.location.href = "/result/pending";
        break;
      case "REFUSED":
        window.location.href = "/result/failed";
        break;
      default:
        window.location.href = "/result/error";
        break;
    }
  }
}

initCheckout();
