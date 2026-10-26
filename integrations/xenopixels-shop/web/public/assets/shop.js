"use strict";
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".checkout").forEach((button) => button.addEventListener("click", async () => {
    const actions = button.closest(".actions");
    const output = document.querySelector("#checkout-result");
    document.querySelectorAll(".checkout").forEach((item) => item.disabled = true);
    output.textContent = "Creating secure checkout…";
    try {
      const response = await fetch("/api/v1/web/checkout", {
        method: "POST", credentials: "same-origin",
        headers: {"Content-Type": "application/json", "X-CSRF-Token": actions.dataset.csrf, "Accept": "application/json"},
        body: JSON.stringify({slug: actions.dataset.slug, provider: button.dataset.provider})
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error?.message || "Checkout could not be started.");
      const target = new URL(result.checkout_url);
      if (target.protocol !== "https:" || !["checkout.stripe.com", "www.paypal.com"].includes(target.hostname)) throw new Error("Payment provider URL was rejected.");
      window.location.assign(target.href);
    } catch (error) {
      output.textContent = error instanceof Error ? error.message : "Checkout could not be started.";
      document.querySelectorAll(".checkout").forEach((item) => item.disabled = false);
    }
  }));
  const productForm = document.querySelector("#product-form");
  if (productForm) {
    productForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      const submit = productForm.querySelector('button[type="submit"]');
      const form = new FormData(productForm);
      const value = (key) => form.get(key) || null;
      const payload = {
        id: value("id"), slug: value("slug"), title: value("title"), description: value("description") || "",
        image_url: value("image_url") || "", web_price_minor: value("web_price_minor"), web_currency: value("web_currency") || "",
        mmoecon_price: value("mmoecon_price"), zeni_cash_price: value("zeni_cash_price"), enabled: form.has("enabled"), reward_json: value("reward_json")
      };
      const output = document.querySelector("#admin-result");
      submit.disabled = true;
      output.textContent = "Saving product…";
      try {
        JSON.parse(payload.reward_json);
        const response = await fetch("/api/v1/web/admin/products", {method:"POST",credentials:"same-origin",headers:{"Content-Type":"application/json","X-CSRF-Token":productForm.dataset.csrf,"Accept":"application/json"},body:JSON.stringify(payload)});
        const result = await response.json();
        if (!response.ok) throw new Error(result.error?.message || "Product could not be saved.");
        output.textContent = "Saved product #" + result.product_id + ". Refreshing catalog…";
        window.location.assign("/admin?saved=" + encodeURIComponent(result.product_id));
      } catch (error) {
        output.textContent = error instanceof Error ? error.message : "Product could not be saved.";
        submit.disabled = false;
      }
    });
    document.querySelectorAll(".edit-product").forEach((button) => button.addEventListener("click", () => {
      const product = JSON.parse(button.dataset.product);
      for (const key of ["slug","title","description","image_url","web_price_minor","web_currency","mmoecon_price","zeni_cash_price"]) productForm.elements[key].value = product[key] ?? "";
      productForm.elements.id.value = product.id;
      productForm.elements.reward_json.value = product.reward_json;
      productForm.elements.enabled.checked = Number(product.enabled) === 1;
      productForm.scrollIntoView({behavior:"smooth",block:"start"});
    }));
  }
  const button = document.querySelector("#create-link");
  if (!button) return;
  button.addEventListener("click", async () => {
    button.disabled = true;
    const output = document.querySelector("#link-result");
    output.textContent = "Generating a code…";
    try {
      const response = await fetch("/api/v1/web/link-code", {
        method: "POST", credentials: "same-origin", headers: {"X-CSRF-Token": button.dataset.csrf, "Accept": "application/json"}
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error?.message || "Could not create the code.");
      output.textContent = `${result.code} — expires in ${Math.ceil(result.expires_in / 60)} minutes`;
    } catch (error) {
      output.textContent = error instanceof Error ? error.message : "Could not create the code.";
      button.disabled = false;
    }
  });
});
