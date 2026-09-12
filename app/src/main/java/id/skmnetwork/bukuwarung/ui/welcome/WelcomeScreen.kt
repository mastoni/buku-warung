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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Person
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
 * Phase 6B.1 — Welcome Screen (Design Master Alignment)
 *
 * Visual hierarchy:
 * 1. Branding / Logo (Storefront icon + Buku Warung + Pembukuan Warung Kecil)
 * 2. Headline:
 *    "Catat Jualan"
 *    "Kelola Keuangan"
 *    "Usaha Makin Maju!"
 * 3. Friendly Warung Storefront illustration
 * 4. Primary CTA: "Mulai Sekarang ->"
 * 5. Google Sheets trust card: "Data Anda tersimpan di Google Sheets Anda sendiri"
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
                .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // 1. BRANDING / LOGO SECTION
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = AppSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.GreenPrimary)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Buku Warung Logo",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(AppSpacing.sm))

                Text(
                    text = "Buku Warung",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.GreenDark,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Pembukuan Warung Kecil",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary
                )
            }

            // ==========================================
            // 2. HEADLINE CALLOUT
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = AppSpacing.xs)
            ) {
                Text(
                    text = "Catat Jualan",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenDark
                )
                Text(
                    text = "Kelola Keuangan",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenDark
                )
                Text(
                    text = "Usaha Makin Maju!",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.GreenPrimary
                )
            }

            // ==========================================
            // 3. WARUNG STOREFRONT ILLUSTRATION
            // ==========================================
            WarungStorefrontArtwork()

            // ==========================================
            // 4. CALL TO ACTION & TRUST CARD
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.sm)
            ) {
                // Primary Start Button
                Button(
                    onClick = onStartSetup,
                    shape = AppShapes.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(4.dp, AppShapes.PillShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Mulai Sekarang",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.md))

                // Google Sheets Trust Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEBF7F0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCE8D7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Google Sheets",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = "Data Anda tersimpan di Google Sheets Anda sendiri",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.GreenDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Warung Storefront artwork composition matching Design Master:
 * - Striped green & white awning
 * - Smiling shopkeeper avatar
 * - Wooden counter labeled "WARUNG" with snack displays
 */
@Composable
private fun WarungStorefrontArtwork() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F7EF),
                        Color(0xFFF9FDFB)
                    )
                )
            )
            .border(1.dp, Color(0xFFD4EEDF), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Awning (Kanopi Warung Belang Hijau & Putih)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                repeat(10) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .background(
                                color = if (i % 2 == 0) AppColors.GreenPrimary else Color(0xFFE8F7EF),
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                    )
                }
            }

            // Middle Scene: Smiling Shopkeeper & Warung Goodies
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Snack Jar / Display
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE58F)),
                    modifier = Modifier.size(width = 54.dp, height = 62.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🍬", fontSize = 16.sp)
                        Text("Snack", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD46B08))
                    }
                }

                // Central Friendly Shopkeeper
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, AppColors.GreenPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Pemilik Warung",
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Right Beverage / Goods Display
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF91D5FF)),
                    modifier = Modifier.size(width = 54.dp, height = 62.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("☕", fontSize = 16.sp)
                        Text("Kopi", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF096DD9))
                    }
                }
            }

            // Counter Desk (Meja Kasir Warung)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFFC49A6C), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "— WARUNG —",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
