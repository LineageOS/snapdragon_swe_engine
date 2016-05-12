/*
 *  Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are
 *  met:
 *      * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.chrome.ChromeSwitches;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.UrlUtilities;
import org.codeaurora.swe.util.SWEUrlUtils;

import java.util.List;
import java.util.ArrayList;


// SWE-feature-Clear-Browser-Data
public final class PageScaleManager {

  private static PageScaleManager sPageScaleManager = null;

  public static PageScaleManager getInstance() {
    if (sPageScaleManager == null) {
      if (!CommandLine.getInstance().hasSwitch(
                ChromeSwitches.PAGE_SCALE_PER_DOMAIN)) {
          CommandLine.getInstance().appendSwitch(
                    ChromeSwitches.PAGE_SCALE_PER_DOMAIN);
      }
      sPageScaleManager = new PageScaleManager();
    }
    return sPageScaleManager;
  }

  /**
   * @param domain Refer to domain.tld
   *         (matches domain.tld and all sub-domains.tld)
   *        This would imply setting the same page scale for all the sub-domains
   *        For eg: If we set page_scale_factor for codeaurora.org (domain.tld)
   *                the page scale codeaurora.org and any of its subdomains
   *                (mail.codeaurora.org) would also be the same.
   *
   * @param page_scale_factor The value by which the page will be scaled
   */
  public void setPageScaleFactor(String domainOrUrl , double page_scale_factor) {
    if (TextUtils.isEmpty(domainOrUrl))
      return;

    domainOrUrl = SWEUrlUtils.getDomainName(UrlUtilities.fixUrl(domainOrUrl));
    PrefServiceBridge.getInstance().setPageScaleFactor(domainOrUrl, page_scale_factor);
  }

  /**
   * @param domain Refer to domain.tld, all sub-domains or a url
   *        Eg: When quering page_scale_factor for "codeaurora.org" or
   *             "mail.codeaurora.org" or "http://codeaurora.org",
   *              all them would return the same value
   */
  public double getPageScaleFactor(String domainOrUrl) {
    if (TextUtils.isEmpty(domainOrUrl))
      return -1;

    // for getting pagescale factor domain or url doesn't matter
    domainOrUrl = UrlUtilities.fixUrl(domainOrUrl);

    return PrefServiceBridge.getInstance().getPageScaleFactor(domainOrUrl);
  }

  /**
   * @param domain Refer to domain.tld and all sub-domains
   *         Eg: When clear page_scale_factor for "codeaurora.org" or
   *             mail.codeaurora.org" or "http://codeaurora.org"
   *             It would clear the page scale for top_level_domain
   *             "codeaurora.org
   */
  public void clearPageScaleFactor(String domainOrUrl) {
    if (TextUtils.isEmpty(domainOrUrl))
      return;

    domainOrUrl = SWEUrlUtils.getDomainName(UrlUtilities.fixUrl(domainOrUrl));

    PrefServiceBridge.getInstance().clearPageScaleFactor(domainOrUrl);
  }

  /**
   * The function clears all the domains
   * whose page scale value were stored
   */
  public void clearAllPageScaleFactor() {
    PrefServiceBridge.getInstance().clearAllPageScaleFactor();
  }

  /**
   * This function returns the list of all the domains
   * whose value has been set through user action of pinch zoom
   * or whose value has been explicitly set using setPageScaleFactor
   */
  public List<String> getAllPageScaleFactor() {
    return PrefServiceBridge.getInstance().getAllPageScaleFactor();
  }

}
// SWE-feature-Clear-Browser-Data

