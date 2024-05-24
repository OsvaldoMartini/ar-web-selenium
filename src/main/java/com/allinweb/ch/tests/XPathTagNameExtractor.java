package com.allinweb.ch.tests;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XPathTagNameExtractor {
    public static void main(String[] args) {

        // Sample list of XPaths
        List<String> xPaths = new ArrayList<>();
        xPaths.add("/html[1]/body[1]/div[3]/form[1]/div[1]/inputMARTINI[1]");
        xPaths.add("/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]");
        xPaths.add("/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]");
        xPaths.add(
                "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shell-outlet-receiver[1]/div[1]/div[1]/div[4]/button[1]");

        xPaths.addAll(Arrays.asList(new String[] {
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/assl-dashboard-retail-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/div[1]/avq-quick-link-container[1]/div[1]/avq-quick-link[1]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/mat-tab-group[1]/div[1]/mat-tab-body[1]/div[1]/avq-payments-dashboard[1]/avq-payments-simple-dashboard[1]/div[1]/div[2]/avq-web-banking-payment-new-payment-quick-links-banklet[1]/avq-payment-new-payment-quick-links[1]/avq-payment-quick-link-container[1]/div[1]/avq-payment-quick-link[2]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shell-outlet-receiver[1]/div[1]/div[1]/div[4]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[1]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/div[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/avq-payment-beneficiary-address-form[1]/div[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/avq-payment-beneficiary-address-form[1]/div[1]/div[3]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/avq-payment-beneficiary-address-form[1]/div[1]/div[4]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[2]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-payment-details-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/div[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[2]/mat-select[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[2]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-payment-details-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/div[1]/div[3]/span[1]/mat-form-field[1]/div[1]/div[1]/div[2]/textarea[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[2]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-payment-details-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/div[1]/div[2]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shell-outlet-receiver[1]/div[1]/div[1]/div[4]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat-toolbar-row[1]/button[4]",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/assl-dashboard-retail-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/div[1]/avq-quick-link-container[1]/div[1]/avq-quick-link[1]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/mat-tab-group[1]/div[1]/mat-tab-body[1]/div[1]/avq-payments-dashboard[1]/avq-payments-simple-dashboard[1]/div[1]/div[2]/avq-web-banking-payment-new-payment-quick-links-banklet[1]/avq-payment-new-payment-quick-links[1]/avq-payment-quick-link-container[1]/div[1]/avq-payment-quick-link[2]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[1]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/div[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/avq-payment-beneficiary-address-form[1]/div[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-content[1]/div[1]/avq-payments-new-page[1]/avq-portal-page-layout[1]/section[1]/div[1]/div[1]/avq-web-banking-payment-entry-banklet[1]/avq-payment-entry[1]/div[1]/avq-web-banking-stepper[1]/div[1]/mat-horizontal-stepper[1]/div[1]/div[2]/div[1]/avq-payment-form-step-layout[1]/div[1]/section[1]/avq-payment-entry-beneficiary-form[1]/form[1]/avq-form-panel[2]/div[1]/mat-expansion-panel[1]/div[1]/div[1]/avq-payment-beneficiary-address-form[1]/div[1]/div[3]/mat-form-field[1]/div[1]/div[1]/div[2]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/mat-option[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/avq-select-search[1]/div[1]/mat-form-field[1]/div[1]/div[1]/div[3]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[1]/div[1]/div[4]/div[1]/div[2]/button[4]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[1]/div[1]/input[1]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[2]/div[1]/input[1]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[5]/div[1]/input[1]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[7]/div[1]/select[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav[1]/div",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav[1]/div",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/button[2]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[9]/div[1]/textarea[1]",
            "/html[1]/body[1]/main[1]/section[2]/div[1]/div[1]/form[1]/div[8]/div[1]/select[1]/option[4]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/avq-app-shel",
            "/html[1]/body[1]/div[3]/form[1]/div[1]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[2]/input[1]",
            "/html[1]/body[1]/div[3]/form[1]/div[3]/div[1]/input[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/mat-option[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/mat-dialog-container[1]/div[1]/div[1]/avq-ui-notification-dialog[1]/div[2]/div[2]/",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/button[1]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/header[1]/avq-app-bar[1]/mat-toolbar[1]/mat",
            "/html[1]/body[1]/div[4]/div[2]/div[1]/div[1]/div[1]/avq-user-account-menu[1]/button[4]",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/mat-sidenav-container[1]/mat-sidenav-conten",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/div[1]/avq-a",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/div[1]/avq-a",
            "/html[1]/body[1]/assl-root[1]/avq-web-banking-portal[1]/avq-portal-layout[1]/footer[1]/div[1]/div[1]/div[1]/div[1]/avq-a",
            "/html[1]/body[1]/div[1]/header[1]/section[1]/div[1]/section[2]/div[2]/div[1]/ul[1]/li[3]/a[1]",
            "/HTML/BODY/HEADER/DIV/NAV/UL/LI[5]/DIV/DIV/DIV/UL/LI/A",
            "/HTML/BODY/DIV/DIV/DIV[3]/DIV[4]/DIV/UL/LI[3]/A",
            "/HTML/BODY/DIV/DIV/DIV[3]/DIV/DIV[2]/DIV[2]/A",
            "/HTML/BODY/DIV/DIV/DIV[3]/DIV[4]/DIV/UL/LI[2]/A",
            "/HTML/BODY/HEADER/DIV/A",
            "/HTML/BODY/DIV/HEADER/SECTION/DIV/SECTION[2]/DIV[2]/DIV/UL/LI[3]/A"
        }));

        // Correct and print the cleaned up XPaths
        for (String xPath : xPaths) {
            String cleanedXPath = removeTrailingSlash(xPath);
            if (!cleanedXPath.equalsIgnoreCase(xPath)) {
                System.out.println(String.format("Cleaned XPath: %s  -> To: %s", xPath, cleanedXPath));
            }
        }

        // Add more XPaths as needed...

        // Extract and print the tag names
        for (String xPath : xPaths) {
            xPath = removeTrailingSlash(xPath);
            String tagName = extractTagName(xPath);
            if (Strings.isNullOrEmpty(tagName)) {
                System.out.println("Tag name: NULL FOR -> " + xPath);
            } else {
                System.out.println("Tag name: " + tagName);
            }
        }
    }

    public static String extractTagName(String xPath) {
        // Find the position of the last '/'
        int lastSlashIndex = xPath.lastIndexOf("/");

        // Extract the substring after the last '/'
        String lastSegment = xPath.substring(lastSlashIndex + 1);

        // If the last segment contains '[', extract the tag name before it
        int bracketIndex = lastSegment.indexOf("[");
        if (bracketIndex != -1) {
            return lastSegment.substring(0, bracketIndex);
        }

        // Return the last segment as the tag name
        return lastSegment;
    }

    /**
     * Removes the trailing slash from an XPath if it ends with one.
     *
     * @param xPath the original XPath string
     * @return the cleaned XPath string without a trailing slash
     */
    public static String removeTrailingSlash(String xPath) {
        if (xPath != null && xPath.endsWith("/")) {
            return xPath.substring(0, xPath.length() - 1);
        }
        return xPath;
    }
}
