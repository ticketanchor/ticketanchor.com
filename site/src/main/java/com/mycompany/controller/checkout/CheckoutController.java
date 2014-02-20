/*
  * Copyright 2008-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mycompany.controller.checkout;

import java.math.BigDecimal;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.payment.PaymentType;
import org.broadleafcommerce.common.payment.dto.CreditCardDTO;
import org.broadleafcommerce.common.payment.dto.PaymentRequestDTO;
import org.broadleafcommerce.common.payment.dto.PaymentResponseDTO;
import org.broadleafcommerce.common.payment.service.PaymentGatewayHostedService;
import org.broadleafcommerce.common.vendor.service.exception.PaymentException;
import org.broadleafcommerce.core.checkout.service.exception.CheckoutException;
import org.broadleafcommerce.core.checkout.service.workflow.CheckoutResponse;
import org.broadleafcommerce.core.order.domain.NullOrderImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.core.web.checkout.model.BillingInfoForm;
import org.broadleafcommerce.core.web.checkout.model.CustomerCreditInfoForm;
import org.broadleafcommerce.core.web.checkout.model.GiftCardInfoForm;
import org.broadleafcommerce.core.web.checkout.model.OrderInfoForm;
import org.broadleafcommerce.core.web.checkout.model.ShippingInfoForm;
import org.broadleafcommerce.core.web.controller.checkout.BroadleafCheckoutController;
import org.broadleafcommerce.core.web.order.CartState;
import org.broadleafcommerce.vendor.paypal.service.payment.MessageConstants;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationError;

@Controller
public class CheckoutController extends BroadleafCheckoutController {

    @RequestMapping("/checkout")
    public String checkout(HttpServletRequest request, HttpServletResponse response, Model model,
            @ModelAttribute("orderInfoForm") OrderInfoForm orderInfoForm,
            @ModelAttribute("shippingInfoForm") ShippingInfoForm shippingForm,
            @ModelAttribute("billingInfoForm") BillingInfoForm billingForm,
            @ModelAttribute("giftCardInfoForm") GiftCardInfoForm giftCardInfoForm,
            @ModelAttribute("customerCreditInfoForm") CustomerCreditInfoForm customerCreditInfoForm,
            RedirectAttributes redirectAttributes) {
        return super.checkout(request, response, model, redirectAttributes);
    }

    @RequestMapping(value = "/checkout/savedetails", method = RequestMethod.POST)
    public String saveGlobalOrderDetails(HttpServletRequest request, Model model,
            @ModelAttribute("shippingInfoForm") ShippingInfoForm shippingForm,
            @ModelAttribute("billingInfoForm") BillingInfoForm billingForm,
            @ModelAttribute("giftCardInfoForm") GiftCardInfoForm giftCardInfoForm,
            @ModelAttribute("orderInfoForm") OrderInfoForm orderInfoForm, BindingResult result) throws ServiceException {
        return super.saveGlobalOrderDetails(request, model, orderInfoForm, result);
    }

    @RequestMapping(value = "/checkout/complete", method = RequestMethod.POST)
    public String processCompleteCheckoutOrderFinalized(RedirectAttributes redirectAttributes) throws PaymentException {
        return super.processCompleteCheckoutOrderFinalized(redirectAttributes);
    }

    @RequestMapping(value = "/checkout/cod/complete", method = RequestMethod.POST)
    public String processPassthroughCheckout(RedirectAttributes redirectAttributes)
            throws PaymentException, PricingException {
        return super.processPassthroughCheckout(redirectAttributes, PaymentType.COD);
    }

    @InitBinder
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
        super.initBinder(request, binder);
    }
    
    @Resource(name = "blPayPalExpressHostedService")
    private PaymentGatewayHostedService paymentGatewayHostedService;
    
    @RequestMapping(value = "/checkout/paypal", method = RequestMethod.POST)
    public String checkoutWithPaypal(@RequestParam("COMPLETE_CHECKOUT_ON_CALLBACK") boolean completeCheckoutOnCallback) throws PaymentException, PricingException {
    	
    	PaymentRequestDTO requestDTO = dtoTranslationService.translateOrder(CartState.getCart());
    	
        String url = "";

        if (requestDTO != null) {
        	
        	requestDTO.completeCheckoutOnCallback(completeCheckoutOnCallback);

            
            try {
                PaymentResponseDTO responseDTO = paymentGatewayHostedService.requestHostedEndpoint(requestDTO);
                url = responseDTO.getResponseMap().get(MessageConstants.REDIRECTURL).toString();

                //https://developer.paypal.com/docs/classic/express-checkout/integration-guide/ECCustomizing/
                if (requestDTO.isCompleteCheckoutOnCallback()) {
                    url = url + "&useraction=commit";
                }

            } catch (PaymentException e) {
                throw new RuntimeException("Unable to Create the PayPal Express Link", e);
            }
        }
        
        return "redirect:" + url;
    }
    
    @RequestMapping(value = "/checkout/creditcard", method = RequestMethod.POST)
	public String checkoutWithCreditCard(HttpServletRequest request,
			HttpServletResponse response, Model model,
			RedirectAttributes redirectAttributes) throws PaymentException, PricingException, Exception {

		BraintreeGateway gateway = new BraintreeGateway(
				Environment.SANDBOX,
				"6hynnwvcqd23yrd5", 
				"6chh2mnkcbpz58ff",
				"450e8f6c0e2d24efdc520937706aa30d");

		PaymentRequestDTO requestDTO = dtoTranslationService.translateOrder(CartState.getCart());
		
		String cardholderName = request.getParameter("creditCard.creditCardHolderName");
		String cardNumber = request.getParameter("creditCard.creditCardNum");
		String expirationDate = request.getParameter("creditCard.creditCardExpDate");
		String cvv = request.getParameter("creditCard.creditCardCvv");
		String transactionTotol = requestDTO.getTransactionTotal();
		TransactionRequest txrequest = new TransactionRequest()
				.amount(new BigDecimal(transactionTotol))
				.creditCard()
				.cardholderName(cardholderName)
				.number(cardNumber)
				.expirationDate(expirationDate)
				.cvv(cvv)
				.done();

		Result<Transaction> result = gateway.transaction().sale(txrequest);

		if (result.isSuccess()) {
			Transaction transaction = result.getTarget();
			System.out.println("Success!: " + transaction.getId());
			return super.processPassthroughCheckout(redirectAttributes, PaymentType.CREDIT_CARD);
		} else {
			if (result.getTransaction() != null) {
				Transaction transaction = result.getTransaction();
				System.out.println("Error processing transaction:");
				System.out.println("  Status: " + transaction.getStatus());
				System.out.println("  Code: "+ transaction.getProcessorResponseCode());
				System.out.println("  Text: "+ transaction.getProcessorResponseText());
			} else {
				for (ValidationError error : result.getErrors().getAllDeepValidationErrors()) {
					System.out.println("Attribute: " + error.getAttribute());
					System.out.println("  Code: " + error.getCode());
					System.out.println("  Message: " + error.getMessage());
				}
			}
			return "redirect:/checkout/error";
		}

	}
    
}
