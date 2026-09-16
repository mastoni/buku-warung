package id.skmnetwork.bukuwarung.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing

/**
 * Clean & Friendly Welcome / Splash Landing Screen (Design Master Alignment)
 *
 * Hierarchy:
 * 1. Logo / Brand Mark: Storefront badge + "Buku Warung" + short subtitle
 * 2. Headline:
 *    "Catat Jualan"
 *    "Kelola Keuangan"
 *    "Usaha Makin Maju!"
 * 3. Friendly Warung Storefront Visual Card (Clean & Airy, White + Green)
 * 4. Primary CTA: "Mulai Sekarang ->"
 * 5. Subtle Google Sheets Trust Caption
 */
@Composable
fun WelcomeScreen(
    onStartSetup: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // 1. BRANDING / LOGO SECTION
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.GreenPrimary)
                        .shadow(3.dp, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Buku Warung Logo",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Buku Warung",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenDark,
                    letterSpacing = 0.3.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Aplikasi Pembukuan & Kasir Warung",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            // ==========================================
            // 2. HEADLINE VALUE PROPOSITION
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Catat Jualan",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "Kelola Keuangan",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Usaha Makin Maju!",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.GreenPrimary
                )
            }

            Spacer(Modifier.height(16.dp))

            // ==========================================
            // 3. FRIENDLY WARUNG STOREFRONT ARTWORK
            // ==========================================
            WarungStorefrontArtwork()

            Spacer(Modifier.height(20.dp))

            // ==========================================
            // 4. CALL TO ACTION & SUBTLE TRUST CAPTION
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Primary CTA Button
                Button(
                    onClick = onStartSetup,
                    shape = AppShapes.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(3.dp, AppShapes.PillShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Mulai Sekarang",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Subtle Google Sheets Trust Caption
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Data tersimpan di Google Sheets Anda sendiri",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Clean & Friendly Warung Storefront Visual composition
 */
@Composable
private fun WarungStorefrontArtwork() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF4FAF6),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDF0E4)),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Awning (Kanopi Garis Hijau & Putih Halus)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                repeat(8) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .background(
                                color = if (i % 2 == 0) AppColors.GreenPrimary else Color(0xFFE4F6EB),
                                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                            )
                    )
                }
            }

            // Central Friendly Storefront & Feature Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Feature 1: POS / Kasir
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2EBE4)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = "Kasir Cepat",
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Central Storefront Circle
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.5.dp, AppColors.GreenPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Warung",
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Feature 2: Laporan & Nota
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2EBE4)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Struk & Nota",
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Bottom Green Accent Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(AppColors.GreenPrimary.copy(alpha = 0.35f))
            )
        }
    }
}
